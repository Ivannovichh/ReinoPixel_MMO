package red;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Clase PaqueteEntrada.
 * Envoltorio para ByteBuffer que facilita la lectura secuencial de los 
 * paquetes binarios recibidos desde los clientes.
 * Mantiene un cursor interno que avanza automáticamente a medida que se extraen datos.
 */
public class PaqueteEntrada {
    
    private ByteBuffer buffer;

    /**
     * Constructor principal.
     * Envuelve el array de bytes crudos recibido desde la red en un buffer
     * de lectura gestionado por Java NIO.
     */
    public PaqueteEntrada(byte[] datos) {
        this.buffer = ByteBuffer.wrap(datos);
    }

    /**
     * leerByte
     * Extrae el siguiente byte (8 bits) del flujo.
     * Generalmente el primer byte leído siempre es el Opcode.
     */
    public byte leerByte() {
        return buffer.get();
    }

    /**
     * leerInt
     * Extrae un número entero (32 bits) del flujo.
     */
    public int leerInt() {
        return buffer.getInt();
    }

    /**
     * leerFloat
     * Extrae un número decimal (32 bits), típicamente usado para coordenadas.
     */
    public float leerFloat() {
        return buffer.getFloat();
    }

    /**
     * leerString
     * Extrae una cadena de texto.
     * Primero lee un 'short' para averiguar cuántos bytes componen el texto,
     * y luego extrae exactamente esa cantidad para decodificarlos como UTF-8.
     */
    public String leerString() {
        short longitud = buffer.getShort();
        byte[] bytesTexto = new byte[longitud];
        buffer.get(bytesTexto);
        return new String(bytesTexto, StandardCharsets.UTF_8);
    }
}