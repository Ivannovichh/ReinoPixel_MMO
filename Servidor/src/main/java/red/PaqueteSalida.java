package red;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Clase PaqueteSalida.
 * Herramienta de serialización que convierte datos primitivos (int, float, String)
 * en un flujo continuo de bytes puros (Binario Puro). 
 * Se utiliza para construir los mensajes antes de enviarlos al cliente (Godot),
 * minimizando el ancho de banda consumido.
 */
public class PaqueteSalida {
    
    private ByteArrayOutputStream bufferMemoria;
    private DataOutputStream escritorDatos;

    /**
     * Constructor principal.
     * Inicializa los flujos de memoria en blanco listos para recibir datos.
     */
    public PaqueteSalida() {
        this.bufferMemoria = new ByteArrayOutputStream();
        this.escritorDatos = new DataOutputStream(bufferMemoria);
    }

    /**
     * escribirByte
     * Inserta un valor numérico de 8 bits.
     * Generalmente utilizado como "Opcode" para identificar el tipo de paquete.
     */
    public void escribirByte(int valor) {
        try {
            escritorDatos.writeByte(valor);
        } catch (IOException e) {
            System.err.println("Error escribiendo byte: " + e.getMessage());
        }
    }

    /**
     * escribirInt
     * Inserta un número entero estándar de 32 bits.
     * Ideal para IDs de jugadores, niveles o cantidades de oro.
     */
    public void escribirInt(int valor) {
        try {
            escritorDatos.writeInt(valor);
        } catch (IOException e) {
            System.err.println("Error escribiendo int: " + e.getMessage());
        }
    }

    /**
     * escribirFloat
     * Inserta un número decimal de 32 bits.
     * Estrictamente utilizado para coordenadas espaciales (X, Y, Z) y rotaciones.
     */
    public void escribirFloat(float valor) {
        try {
            escritorDatos.writeFloat(valor);
        } catch (IOException e) {
            System.err.println("Error escribiendo float: " + e.getMessage());
        }
    }

    /**
     * escribirString
     * Convierte un texto a formato UTF-8 y lo empaqueta.
     * Primero inserta un 'short' (16 bits) indicando la longitud exacta de la cadena,
     * seguido de los bytes del texto. Esto permite que el lector sepa cuándo termina.
     */
    public void escribirString(String texto) {
        try {
            byte[] bytesTexto = texto.getBytes(StandardCharsets.UTF_8);
            escritorDatos.writeShort(bytesTexto.length);
            escritorDatos.write(bytesTexto);
        } catch (IOException e) {
            System.err.println("Error escribiendo string: " + e.getMessage());
        }
    }

    /**
     * obtenerBytes
     * Finaliza la escritura y extrae el paquete completo.
     * Retorna el array de bytes puro listo para ser inyectado en el WebSocket.
     */
    public byte[] obtenerBytes() {
        return bufferMemoria.toByteArray();
    }
}