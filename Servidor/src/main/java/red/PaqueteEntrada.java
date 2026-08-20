package red;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Clase PaqueteEntrada.
 * Envoltorio para ByteBuffer que facilita la lectura secuencial de los 
 * paquetes binarios recibidos desde los clientes a través de UDP/KCP.
 * Mantiene un cursor interno que avanza automáticamente a medida que se extraen 
 * datos, garantizando una lectura estricta en formato Big Endian.
 */
public class PaqueteEntrada {
    
    private ByteBuffer buffer;

    /**
     * Constructor principal.
     * Envuelve el array de bytes crudos recibido desde la red nativa en un buffer
     * de lectura gestionado por Java NIO, listo para su extracción secuencial.
     */
    public PaqueteEntrada(byte[] datos) {
        this.buffer = ByteBuffer.wrap(datos);
    }

    /**
     * leerByte
     * Método genérico que extrae el siguiente byte (8 bits) del flujo.
     * Generalmente el primer byte leído en el servidor siempre es el Opcode
     * que define la acción a realizar.
     */
    public byte leerByte() {
        return buffer.get();
    }

    /**
     * leerInt
     * Método genérico que extrae un número entero (32 bits) del flujo.
     * Utilizado para procesar identificadores únicos, variables de estado o cantidades.
     */
    public int leerInt() {
        return buffer.getInt();
    }

    /**
     * leerFloat
     * Método genérico que extrae un número decimal (32 bits).
     * Típicamente usado para descifrar las coordenadas espaciales (X, Y, Z) enviadas
     * por el motor físico del cliente a alta velocidad.
     */
    public float leerFloat() {
        return buffer.getFloat();
    }

    /**
     * leerString
     * Método genérico que extrae una cadena de texto dinámica.
     * Primero lee un entero corto (16 bits) para averiguar la longitud exacta del texto,
     * y luego extrae esos bytes específicos para decodificarlos limpiamente como UTF-8.
     */
    public String leerString() {
        short longitud = buffer.getShort();
        byte[] bytesTexto = new byte[longitud];
        buffer.get(bytesTexto);
        return new String(bytesTexto, StandardCharsets.UTF_8);
    }
}