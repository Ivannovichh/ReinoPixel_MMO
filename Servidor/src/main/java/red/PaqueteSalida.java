package red;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Clase PaqueteSalida.
 * Herramienta de serialización que convierte datos primitivos (int, float, String)
 * en un flujo continuo de bytes puros (Binario Puro). 
 * Se utiliza para construir los mensajes antes de enviarlos al cliente (Godot) 
 * a través de la red UDP/KCP, minimizando el ancho de banda consumido.
 */
public class PaqueteSalida {
    
    private ByteArrayOutputStream bufferMemoria;
    private DataOutputStream escritorDatos;

    /**
     * Constructor principal.
     * Inicializa los flujos de memoria en blanco listos para recibir datos
     * y prepararlos en formato Big Endian (estándar de red).
     */
    public PaqueteSalida() {
        this.bufferMemoria = new ByteArrayOutputStream();
        this.escritorDatos = new DataOutputStream(bufferMemoria);
    }

    /**
     * escribirByte
     * Método genérico para insertar un valor numérico de 8 bits.
     * Generalmente utilizado como "Opcode" para identificar el tipo de paquete
     * o para enviar valores booleanos compactos (1/0).
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
     * Método genérico para insertar un número entero estándar de 32 bits (Big Endian).
     * Ideal para IDs de jugadores, niveles, daño de ataques o cantidades de oro.
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
     * Método genérico para insertar un número decimal de 32 bits (Big Endian).
     * Estrictamente utilizado para coordenadas espaciales (X, Y, Z) y rotaciones continuas.
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
     * Método genérico que convierte un texto a formato UTF-8 y lo empaqueta.
     * Primero inserta un 'short' (16 bits) indicando la longitud exacta de la cadena,
     * seguido de los bytes del texto. Esto permite que Godot sepa cuántos bytes debe leer.
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
     * Método interno que finaliza la escritura y extrae el paquete completo.
     * Retorna el array de bytes puro listo para ser inyectado en el canal KCP (Netty).
     */
    public byte[] obtenerBytes() {
        return bufferMemoria.toByteArray();
    }
}