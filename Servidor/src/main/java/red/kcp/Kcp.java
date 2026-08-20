package red.kcp;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Kcp
 * Puerto directo a Java del protocolo KCP (skywind3000/kcp, ikcp.c) usado por el
 * cliente nativo en C++ (mi_extension_kcp). Reimplementa fielmente la máquina de
 * estados ARQ: fragmentación, reensamblado ordenado, ACKs, retransmisión (RTO),
 * fast-resend y control de congestión, para que el servidor Java decodifique
 * correctamente los datagramas UDP que ya vienen envueltos en el protocolo KCP
 * en lugar de leerlos como si fueran el payload de la aplicación.
 *
 * Codificación de segmentos en el cable (24 bytes de cabecera, poco-endian,
 * IDÉNTICO a ikcp_encode_seg en ikcp.c):
 *   conv(4) cmd(1) frg(1) wnd(2) ts(4) sn(4) una(4) len(4) [datos...]
 */
public class Kcp {

    /** Callback de salida: KCP entrega aquí los bytes crudos listos para ir por UDP. */
    public interface KcpOutput {
        void output(byte[] data, int size, Kcp kcp);
    }

    // ---------------------------------------------------------------
    // Constantes (idénticas a ikcp.c)
    // ---------------------------------------------------------------
    static final int IKCP_RTO_NDL = 30;
    static final int IKCP_RTO_MIN = 100;
    static final int IKCP_RTO_DEF = 200;
    static final int IKCP_RTO_MAX = 60000;
    static final int IKCP_CMD_PUSH = 81;
    static final int IKCP_CMD_ACK = 82;
    static final int IKCP_CMD_WASK = 83;
    static final int IKCP_CMD_WINS = 84;
    static final int IKCP_ASK_SEND = 1;
    static final int IKCP_ASK_TELL = 2;
    static final int IKCP_WND_SND = 32;
    static final int IKCP_WND_RCV = 128;
    static final int IKCP_MTU_DEF = 1400;
    static final int IKCP_INTERVAL = 100;
    static final int IKCP_OVERHEAD = 24;
    static final int IKCP_DEADLINK = 20;
    static final int IKCP_THRESH_INIT = 2;
    static final int IKCP_THRESH_MIN = 2;
    static final int IKCP_PROBE_INIT = 7000;
    static final int IKCP_PROBE_LIMIT = 120000;
    static final int IKCP_FASTACK_LIMIT = 5;

    // ---------------------------------------------------------------
    // Segmento (equivalente a struct IKCPSEG)
    // ---------------------------------------------------------------
    private static class Segment {
        int conv, cmd, frg, wnd;
        int ts, sn, una;
        int len;
        int resendts;
        int rto;
        int fastack;
        int xmit;
        byte[] data;

        Segment(int size) {
            data = new byte[Math.max(size, 0)];
        }
    }

    // ---------------------------------------------------------------
    // Estado (equivalente a struct IKCPCB)
    // ---------------------------------------------------------------
    private final int conv;
    private int mtu = IKCP_MTU_DEF;
    private int mss = mtu - IKCP_OVERHEAD;
    private int state = 0;

    private int snd_una = 0, snd_nxt = 0, rcv_nxt = 0;
    private int ssthresh = IKCP_THRESH_INIT;
    private int rx_rttval = 0, rx_srtt = 0, rx_rto = IKCP_RTO_DEF, rx_minrto = IKCP_RTO_MIN;
    private int snd_wnd = IKCP_WND_SND, rcv_wnd = IKCP_WND_RCV, rmt_wnd = IKCP_WND_RCV, cwnd = 0, probe = 0;
    private int current = 0, interval = IKCP_INTERVAL, ts_flush = IKCP_INTERVAL, xmit = 0;
    private int nrcv_buf = 0, nsnd_buf = 0, nrcv_que = 0, nsnd_que = 0;
    private int nodelay = 0, updated = 0;
    private int ts_probe = 0, probe_wait = 0;
    private final int dead_link = IKCP_DEADLINK;
    private int incr = 0;

    private final LinkedList<Segment> snd_queue = new LinkedList<>();
    private final LinkedList<Segment> rcv_queue = new LinkedList<>();
    private final LinkedList<Segment> snd_buf = new LinkedList<>();
    private final List<Segment> rcv_buf = new ArrayList<>();

    private final List<int[]> acklist = new ArrayList<>(); // cada elemento: {sn, ts}

    private int fastresend = 0;
    private final int fastlimit = IKCP_FASTACK_LIMIT;
    private int nocwnd = 0;

    private byte[] buffer;
    private final KcpOutput output;

    public Kcp(int conv, KcpOutput output) {
        this.conv = conv;
        this.output = output;
        this.buffer = new byte[(mtu + IKCP_OVERHEAD) * 3];
    }

    public int getConv() {
        return conv;
    }

    // ---------------------------------------------------------------
    // Utilidades (equivalentes a _imin_/_imax_/_ibound_/_itimediff)
    // ---------------------------------------------------------------
    private static int imin(int a, int b) { return (a <= b) ? a : b; }
    private static int imax(int a, int b) { return (a >= b) ? a : b; }
    private static int ibound(int lower, int middle, int upper) { return imin(imax(lower, middle), upper); }
    private static int itimediff(int later, int earlier) { return later - earlier; }

    // ---------------------------------------------------------------
    // Codificación / decodificación poco-endian (idéntica a ikcp_encode*/decode*)
    // ---------------------------------------------------------------
    private static int encode8u(byte[] p, int off, int c) {
        p[off] = (byte) c;
        return off + 1;
    }

    private static int decode8u(byte[] p, int off, int[] out) {
        out[0] = p[off] & 0xFF;
        return off + 1;
    }

    private static int encode16u(byte[] p, int off, int w) {
        p[off] = (byte) (w & 0xFF);
        p[off + 1] = (byte) ((w >>> 8) & 0xFF);
        return off + 2;
    }

    private static int decode16u(byte[] p, int off, int[] out) {
        out[0] = (p[off] & 0xFF) | ((p[off + 1] & 0xFF) << 8);
        return off + 2;
    }

    private static int encode32u(byte[] p, int off, int l) {
        p[off] = (byte) (l & 0xFF);
        p[off + 1] = (byte) ((l >>> 8) & 0xFF);
        p[off + 2] = (byte) ((l >>> 16) & 0xFF);
        p[off + 3] = (byte) ((l >>> 24) & 0xFF);
        return off + 4;
    }

    private static int decode32u(byte[] p, int off, int[] out) {
        out[0] = (p[off] & 0xFF) | ((p[off + 1] & 0xFF) << 8)
                | ((p[off + 2] & 0xFF) << 16) | ((p[off + 3] & 0xFF) << 24);
        return off + 4;
    }

    /** Lee el campo 'conv' (4 bytes, poco-endian) del inicio de un datagrama KCP crudo. */
    public static int peekConv(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16) | ((data[offset + 3] & 0xFF) << 24);
    }

    // ---------------------------------------------------------------
    // Configuración (equivalentes a ikcp_setmtu/ikcp_nodelay/ikcp_wndsize)
    // ---------------------------------------------------------------
    public int setMtu(int mtu_) {
        if (mtu_ < 50 || mtu_ < IKCP_OVERHEAD) return -1;
        this.mtu = mtu_;
        this.mss = mtu_ - IKCP_OVERHEAD;
        this.buffer = new byte[(mtu_ + IKCP_OVERHEAD) * 3];
        return 0;
    }

    public void noDelay(int nodelay_, int interval_, int resend, int nc) {
        if (nodelay_ >= 0) {
            this.nodelay = nodelay_;
            this.rx_minrto = (nodelay_ != 0) ? IKCP_RTO_NDL : IKCP_RTO_MIN;
        }
        if (interval_ >= 0) {
            if (interval_ > 5000) interval_ = 5000;
            else if (interval_ < 10) interval_ = 10;
            this.interval = interval_;
        }
        if (resend >= 0) this.fastresend = resend;
        if (nc >= 0) this.nocwnd = nc;
    }

    public void wndSize(int sndwnd, int rcvwnd) {
        if (sndwnd > 0) this.snd_wnd = sndwnd;
        if (rcvwnd > 0) this.rcv_wnd = imax(rcvwnd, IKCP_WND_RCV);
    }

    public int waitSnd() {
        return nsnd_buf + nsnd_que;
    }

    // ---------------------------------------------------------------
    // send (equivalente a ikcp_send) — encola datos de aplicación para envío fiable
    // ---------------------------------------------------------------
    public int send(byte[] data) {
        if (data == null) return send(new byte[0], 0, 0);
        return send(data, 0, data.length);
    }

    public int send(byte[] data, int offset, int len) {
        if (len < 0) return -1;

        int count = (len <= mss) ? 1 : (len + mss - 1) / mss;
        if (count >= IKCP_WND_RCV) return -2;
        if (count == 0) count = 1;

        int ptr = offset;
        for (int i = 0; i < count; i++) {
            int size = Math.min(mss, len);
            Segment seg = new Segment(size);
            if (data != null && size > 0) {
                System.arraycopy(data, ptr, seg.data, 0, size);
            }
            seg.len = size;
            seg.frg = count - i - 1;
            snd_queue.addLast(seg);
            nsnd_que++;
            ptr += size;
            len -= size;
        }
        return 0;
    }

    // ---------------------------------------------------------------
    // recv / peekSize (equivalentes a ikcp_recv / ikcp_peeksize)
    // ---------------------------------------------------------------
    public byte[] recv() {
        if (rcv_queue.isEmpty()) return null;

        int peek = peekSize();
        if (peek < 0) return null;

        boolean recover = nrcv_que >= rcv_wnd;

        byte[] result = new byte[peek];
        int pos = 0;
        Iterator<Segment> it = rcv_queue.iterator();
        while (it.hasNext()) {
            Segment seg = it.next();
            System.arraycopy(seg.data, 0, result, pos, seg.len);
            pos += seg.len;
            it.remove();
            nrcv_que--;
            if (seg.frg == 0) break;
        }

        while (!rcv_buf.isEmpty()) {
            Segment seg = rcv_buf.get(0);
            if (seg.sn == rcv_nxt && nrcv_que < rcv_wnd) {
                rcv_buf.remove(0);
                nrcv_buf--;
                rcv_queue.addLast(seg);
                nrcv_que++;
                rcv_nxt++;
            } else {
                break;
            }
        }

        if (nrcv_que < rcv_wnd && recover) {
            probe |= IKCP_ASK_TELL;
        }

        return result;
    }

    private int peekSize() {
        if (rcv_queue.isEmpty()) return -1;
        Segment seg = rcv_queue.getFirst();
        if (seg.frg == 0) return seg.len;
        if (nrcv_que < seg.frg + 1) return -1;

        int length = 0;
        for (Segment s : rcv_queue) {
            length += s.len;
            if (s.frg == 0) break;
        }
        return length;
    }

    // ---------------------------------------------------------------
    // parse ack / una / fastack / data (equivalentes a ikcp_parse_*)
    // ---------------------------------------------------------------
    private void updateAck(int rtt) {
        if (rx_srtt == 0) {
            rx_srtt = rtt;
            rx_rttval = rtt / 2;
        } else {
            int delta = rtt - rx_srtt;
            if (delta < 0) delta = -delta;
            rx_rttval = (3 * rx_rttval + delta) / 4;
            rx_srtt = (7 * rx_srtt + rtt) / 8;
            if (rx_srtt < 1) rx_srtt = 1;
        }
        int rto = rx_srtt + imax(interval, 4 * rx_rttval);
        rx_rto = ibound(rx_minrto, rto, IKCP_RTO_MAX);
    }

    private void shrinkBuf() {
        if (!snd_buf.isEmpty()) {
            snd_una = snd_buf.getFirst().sn;
        } else {
            snd_una = snd_nxt;
        }
    }

    private void parseAck(int sn) {
        if (itimediff(sn, snd_una) < 0 || itimediff(sn, snd_nxt) >= 0) return;
        Iterator<Segment> it = snd_buf.iterator();
        while (it.hasNext()) {
            Segment seg = it.next();
            if (sn == seg.sn) {
                it.remove();
                nsnd_buf--;
                break;
            }
            if (itimediff(sn, seg.sn) < 0) break;
        }
    }

    private void parseUna(int una) {
        Iterator<Segment> it = snd_buf.iterator();
        while (it.hasNext()) {
            Segment seg = it.next();
            if (itimediff(una, seg.sn) > 0) {
                it.remove();
                nsnd_buf--;
            } else {
                break;
            }
        }
    }

    private void parseFastack(int sn, int ts) {
        if (itimediff(sn, snd_una) < 0 || itimediff(sn, snd_nxt) >= 0) return;
        for (Segment seg : snd_buf) {
            if (itimediff(sn, seg.sn) < 0) break;
            else if (sn != seg.sn) seg.fastack++;
        }
    }

    private void ackPush(int sn, int ts) {
        acklist.add(new int[]{sn, ts});
    }

    private void parseData(Segment newseg) {
        int sn = newseg.sn;
        if (itimediff(sn, rcv_nxt + rcv_wnd) >= 0 || itimediff(sn, rcv_nxt) < 0) {
            return; // fuera de ventana: se descarta
        }

        boolean repeat = false;
        int insertPos = 0;
        for (int i = rcv_buf.size() - 1; i >= 0; i--) {
            Segment seg = rcv_buf.get(i);
            if (seg.sn == sn) {
                repeat = true;
                break;
            }
            if (itimediff(sn, seg.sn) > 0) {
                insertPos = i + 1;
                break;
            }
        }

        if (!repeat) {
            rcv_buf.add(insertPos, newseg);
            nrcv_buf++;
        }

        while (!rcv_buf.isEmpty()) {
            Segment seg = rcv_buf.get(0);
            if (seg.sn == rcv_nxt && nrcv_que < rcv_wnd) {
                rcv_buf.remove(0);
                nrcv_buf--;
                rcv_queue.addLast(seg);
                nrcv_que++;
                rcv_nxt++;
            } else {
                break;
            }
        }
    }

    // ---------------------------------------------------------------
    // input (equivalente a ikcp_input) — decodifica un datagrama UDP crudo
    // ---------------------------------------------------------------
    public int input(byte[] data) {
        return input(data, 0, data == null ? 0 : data.length);
    }

    public int input(byte[] data, int offset, int size) {
        int prevUna = snd_una;
        int maxack = 0, latestTs = 0;
        boolean flag = false;

        if (data == null || size < IKCP_OVERHEAD) return -1;

        int pos = offset;
        int remaining = size;
        int[] tmp = new int[1];

        while (remaining >= IKCP_OVERHEAD) {
            pos = decode32u(data, pos, tmp);
            int convRecv = tmp[0];
            if (convRecv != conv) return -1;

            pos = decode8u(data, pos, tmp);
            int cmd = tmp[0];
            pos = decode8u(data, pos, tmp);
            int frg = tmp[0];
            pos = decode16u(data, pos, tmp);
            int wnd = tmp[0];
            pos = decode32u(data, pos, tmp);
            int ts = tmp[0];
            pos = decode32u(data, pos, tmp);
            int sn = tmp[0];
            pos = decode32u(data, pos, tmp);
            int una = tmp[0];
            pos = decode32u(data, pos, tmp);
            int len = tmp[0];

            remaining -= IKCP_OVERHEAD;

            if (remaining < len || len < 0) return -2;

            if (cmd != IKCP_CMD_PUSH && cmd != IKCP_CMD_ACK
                    && cmd != IKCP_CMD_WASK && cmd != IKCP_CMD_WINS) {
                return -3;
            }

            rmt_wnd = wnd;
            parseUna(una);
            shrinkBuf();

            if (cmd == IKCP_CMD_ACK) {
                if (itimediff(current, ts) >= 0) {
                    updateAck(itimediff(current, ts));
                }
                parseAck(sn);
                shrinkBuf();
                if (!flag) {
                    flag = true;
                    maxack = sn;
                    latestTs = ts;
                } else if (itimediff(sn, maxack) > 0) {
                    maxack = sn;
                    latestTs = ts;
                }
            } else if (cmd == IKCP_CMD_PUSH) {
                if (itimediff(sn, rcv_nxt + rcv_wnd) < 0) {
                    ackPush(sn, ts);
                    if (itimediff(sn, rcv_nxt) >= 0) {
                        Segment seg = new Segment(len);
                        seg.conv = convRecv;
                        seg.cmd = cmd;
                        seg.frg = frg;
                        seg.wnd = wnd;
                        seg.ts = ts;
                        seg.sn = sn;
                        seg.una = una;
                        seg.len = len;
                        if (len > 0) System.arraycopy(data, pos, seg.data, 0, len);
                        parseData(seg);
                    }
                }
            } else if (cmd == IKCP_CMD_WASK) {
                probe |= IKCP_ASK_TELL;
            } // IKCP_CMD_WINS: no hace falta nada

            pos += len;
            remaining -= len;
        }

        if (flag) parseFastack(maxack, latestTs);

        if (itimediff(snd_una, prevUna) > 0) {
            if (cwnd < rmt_wnd) {
                int mssLocal = mss;
                if (cwnd < ssthresh) {
                    cwnd++;
                    incr += mssLocal;
                } else {
                    if (incr < mssLocal) incr = mssLocal;
                    incr += (mssLocal * mssLocal) / incr + (mssLocal / 16);
                    if ((cwnd + 1) * mssLocal <= incr) {
                        cwnd = (incr + mssLocal - 1) / (mssLocal > 0 ? mssLocal : 1);
                    }
                }
                if (cwnd > rmt_wnd) {
                    cwnd = rmt_wnd;
                    incr = rmt_wnd * mssLocal;
                }
            }
        }

        return 0;
    }

    // ---------------------------------------------------------------
    // flush (equivalente a ikcp_flush)
    // ---------------------------------------------------------------
    private int encodeSeg(byte[] buf, int ptr, Segment seg) {
        ptr = encode32u(buf, ptr, seg.conv);
        ptr = encode8u(buf, ptr, seg.cmd);
        ptr = encode8u(buf, ptr, seg.frg);
        ptr = encode16u(buf, ptr, seg.wnd);
        ptr = encode32u(buf, ptr, seg.ts);
        ptr = encode32u(buf, ptr, seg.sn);
        ptr = encode32u(buf, ptr, seg.una);
        ptr = encode32u(buf, ptr, seg.len);
        return ptr;
    }

    private int wndUnused() {
        if (nrcv_que < rcv_wnd) return rcv_wnd - nrcv_que;
        return 0;
    }

    private void flushOutput(int size) {
        if (size <= 0) return;
        byte[] out = new byte[size];
        System.arraycopy(buffer, 0, out, 0, size);
        output.output(out, size, this);
    }

    public void flush() {
        if (updated == 0) return;

        Segment seg = new Segment(0);
        seg.conv = conv;
        seg.cmd = IKCP_CMD_ACK;
        seg.frg = 0;
        seg.wnd = wndUnused();
        seg.una = rcv_nxt;
        seg.len = 0;
        seg.sn = 0;
        seg.ts = 0;

        int ptr = 0;

        int ackCount = acklist.size();
        for (int i = 0; i < ackCount; i++) {
            if (ptr + IKCP_OVERHEAD > mtu) {
                flushOutput(ptr);
                ptr = 0;
            }
            int[] a = acklist.get(i);
            seg.sn = a[0];
            seg.ts = a[1];
            ptr = encodeSeg(buffer, ptr, seg);
        }
        acklist.clear();

        if (rmt_wnd == 0) {
            if (probe_wait == 0) {
                probe_wait = IKCP_PROBE_INIT;
                ts_probe = current + probe_wait;
            } else if (itimediff(current, ts_probe) >= 0) {
                if (probe_wait < IKCP_PROBE_INIT) probe_wait = IKCP_PROBE_INIT;
                probe_wait += probe_wait / 2;
                if (probe_wait > IKCP_PROBE_LIMIT) probe_wait = IKCP_PROBE_LIMIT;
                ts_probe = current + probe_wait;
                probe |= IKCP_ASK_SEND;
            }
        } else {
            ts_probe = 0;
            probe_wait = 0;
        }

        if ((probe & IKCP_ASK_SEND) != 0) {
            seg.cmd = IKCP_CMD_WASK;
            if (ptr + IKCP_OVERHEAD > mtu) {
                flushOutput(ptr);
                ptr = 0;
            }
            ptr = encodeSeg(buffer, ptr, seg);
        }

        if ((probe & IKCP_ASK_TELL) != 0) {
            seg.cmd = IKCP_CMD_WINS;
            if (ptr + IKCP_OVERHEAD > mtu) {
                flushOutput(ptr);
                ptr = 0;
            }
            ptr = encodeSeg(buffer, ptr, seg);
        }

        probe = 0;

        int cwndCalc = imin(snd_wnd, rmt_wnd);
        if (nocwnd == 0) cwndCalc = imin(cwnd, cwndCalc);

        while (itimediff(snd_nxt, snd_una + cwndCalc) < 0) {
            if (snd_queue.isEmpty()) break;
            Segment newseg = snd_queue.pollFirst();
            snd_buf.addLast(newseg);
            nsnd_que--;
            nsnd_buf++;

            newseg.conv = conv;
            newseg.cmd = IKCP_CMD_PUSH;
            newseg.wnd = seg.wnd;
            newseg.ts = current;
            newseg.sn = snd_nxt++;
            newseg.una = rcv_nxt;
            newseg.resendts = current;
            newseg.rto = rx_rto;
            newseg.fastack = 0;
            newseg.xmit = 0;
        }

        int resent = (fastresend > 0) ? fastresend : Integer.MAX_VALUE;
        int rtomin = (nodelay == 0) ? (rx_rto >> 3) : 0;
        boolean lost = false;
        int change = 0;

        for (Segment segment : snd_buf) {
            boolean needsend = false;
            if (segment.xmit == 0) {
                needsend = true;
                segment.xmit++;
                segment.rto = rx_rto;
                segment.resendts = current + segment.rto + rtomin;
            } else if (itimediff(current, segment.resendts) >= 0) {
                needsend = true;
                segment.xmit++;
                xmit++;
                if (nodelay == 0) {
                    segment.rto += imax(segment.rto, rx_rto);
                } else {
                    int step = (nodelay < 2) ? segment.rto : rx_rto;
                    segment.rto += step / 2;
                }
                segment.resendts = current + segment.rto;
                lost = true;
            } else if (segment.fastack >= resent) {
                if (segment.xmit <= fastlimit || fastlimit <= 0) {
                    needsend = true;
                    segment.xmit++;
                    segment.fastack = 0;
                    segment.resendts = current + segment.rto;
                    change++;
                }
            }

            if (needsend) {
                segment.ts = current;
                segment.wnd = seg.wnd;
                segment.una = rcv_nxt;

                int need = IKCP_OVERHEAD + segment.len;
                if (ptr + need > mtu) {
                    flushOutput(ptr);
                    ptr = 0;
                }

                ptr = encodeSeg(buffer, ptr, segment);
                if (segment.len > 0) {
                    System.arraycopy(segment.data, 0, buffer, ptr, segment.len);
                    ptr += segment.len;
                }

                if (segment.xmit >= dead_link) {
                    state = -1;
                }
            }
        }

        flushOutput(ptr);

        if (change != 0) {
            int inflight = snd_nxt - snd_una;
            ssthresh = Math.max(inflight / 2, IKCP_THRESH_MIN);
            cwnd = ssthresh + resent;
            incr = cwnd * mss;
        }

        if (lost) {
            ssthresh = Math.max(cwndCalc / 2, IKCP_THRESH_MIN);
            cwnd = 1;
            incr = mss;
        }

        if (cwnd < 1) {
            cwnd = 1;
            incr = mss;
        }
    }

    // ---------------------------------------------------------------
    // update / check (equivalentes a ikcp_update / ikcp_check)
    // ---------------------------------------------------------------
    public void update(int currentMs) {
        current = currentMs;
        if (updated == 0) {
            updated = 1;
            ts_flush = current;
        }
        int slap = itimediff(current, ts_flush);
        if (slap >= 10000 || slap < -10000) {
            ts_flush = current;
            slap = 0;
        }
        if (slap >= 0) {
            ts_flush += interval;
            if (itimediff(current, ts_flush) >= 0) {
                ts_flush = current + interval;
            }
            flush();
        }
    }

    public boolean isDead() {
        return state == -1;
    }
}
