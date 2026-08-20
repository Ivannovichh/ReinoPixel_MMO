package red;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
     * Como el protocolo KCP ya ha limpiado las cabeceras de red, el flujo 
     * contiene los datos puros. Leemos un entero corto (16 bits) para averiguar
     * la longitud y extraemos exactamente esos bytes.
     */
    public String leerString() {
        // Verificación defensiva interna: aseguramos que existen al menos 2 bytes
        // en el búfer para poder extraer la longitud de la cadena sin lanzar excepciones.
        if (buffer.remaining() < 2) {
            System.err.println("¡CRÍTICO! Búfer insuficiente para leer la longitud del string.");
            return "";
        }
        
        // Extraemos un entero corto (16 bits) sin signo.
        // Esto sincroniza perfectamente con el put_16() de Godot.
        int longitud = Short.toUnsignedInt(buffer.getShort());
        
        // Validación cruzada para evitar desbordamientos si el cliente envía datos corruptos.
        if (longitud <= 0 || longitud > buffer.remaining()) {
            return "";
        }
        
        // Extracción limpia y volcado de memoria a la cadena final.
        byte[] bytes = new byte[longitud];
        buffer.get(bytes);
        
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}