package red;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Clase PaqueteEntrada.
 * Envoltorio robusto para ByteBuffer que facilita la lectura secuencial de los 
 * paquetes binarios recibidos desde UDP asegurando la alineación Big Endian.
 */
public class PaqueteEntrada {
    
    private ByteBuffer buffer;

    /**
     * PaqueteEntrada
     * Método constructor genérico. Recibe el array de bytes crudos de la red,
     * lo envuelve en un ByteBuffer de Java NIO, fuerza el formato Big Endian
     * y sitúa el cursor estrictamente al inicio (posición 0).
     */
    public PaqueteEntrada(byte[] datos) {
        this.buffer = ByteBuffer.wrap(datos);
        this.buffer.order(ByteOrder.BIG_ENDIAN);
        this.buffer.position(0);
    }

    /**
     * leerByte
     * Método interno para extraer el siguiente byte (8 bits) del flujo de datos.
     * Utilizado principalmente para leer el Opcode inicial del paquete.
     */
    public byte leerByte() {
        return buffer.get();
    }

    /**
     * leerShort
     * Método interno para extraer un número entero corto (16 bits) del búfer.
     */
    public short leerShort() {
        return buffer.getShort();
    }

    /**
     * leerInt
     * Método interno para extraer un número entero de 32 bits del flujo.
     */
    public int leerInt() {
        return buffer.getInt();
    }

    /**
     * leerFloat
     * Método interno para extraer un número decimal de 32 bits.
     */
    public float leerFloat() {
        return buffer.getFloat();
    }

    /**
     * getBuffer
     * Método genérico de acceso que devuelve la instancia subyacente del ByteBuffer.
     */
    public ByteBuffer getBuffer() {
        return buffer;
    }

    /**
     * leerString
     * Método genérico interno encargado de extraer cadenas de texto dinámicas.
     */
    public String leerString() {
        if (buffer.remaining() < 2) {
            System.err.println("¡CRÍTICO! No quedan suficientes bytes para leer la longitud del string. Quedan: " + buffer.remaining());
            return "";
        }
        
        short longitudShort = buffer.getShort();
        int longitud = Short.toUnsignedInt(longitudShort);
        
        System.out.println("DEBUG LEER STRING -> Longitud leída (short): " + longitudShort + " | Como entero: " + longitud + " | Bytes restantes: " + buffer.remaining());
        
        if (longitud <= 0 || longitud > buffer.remaining()) {
            System.err.println("¡CRÍTICO! Longitud inválida o excede el buffer restante.");
            return "";
        }
        
        byte[] bytes = new byte[longitud];
        buffer.get(bytes);
        
        String textoLeido = new String(bytes, StandardCharsets.UTF_8);
        System.out.println("DEBUG LEER STRING -> Texto decodificado con éxito: [" + textoLeido + "]");
        return textoLeido;
    }
}