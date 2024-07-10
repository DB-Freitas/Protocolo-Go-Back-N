/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.redes;

/*
 * @author flavio
 */

import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.Semaphore;
import java.util.Timer;
import java.util.TimerTask;

@SuppressWarnings("ALL")
public class EnviaDados extends Thread {

    private final int portaLocalEnvio = 2000;
    private final int portaDestino = 2001;
    private final int portaLocalRecebimento = 2003;
    Semaphore sem;
    private final String funcao;

    String BLUE = "\u001B[34m";
    int windowSize;

    private DatagramSocket datagramSocket;


    private ArrayList<Timer> timers;
    private ArrayList<byte[]> bufferDados = new ArrayList<>();

    private void startTimer(int id){
        Timer timer;
        if(timers.get(id) != null) {timer = timers.get(id);}
        else {timer = new Timer(); timers.set(id,timer);}

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println(BLUE + "Timeout para ACK " + id);
                reenviaPct(id);
            }
        }, 5);
    }

    private void stopTimer(int id){
        Timer timer = timers.get(id);
        if(timer != null){
            timer.cancel();
            timers.set(id,null);
        }
        System.out.println(BLUE + "Espera para ACK " + id + " terminada.");
    }

    public EnviaDados(Semaphore sem, String funcao, int windowSize, ArrayList<Timer> timers) {
        super(funcao);
        this.sem = sem;
        this.funcao = funcao;
        this.windowSize = windowSize;
        this.timers = timers;
        try {
            this.datagramSocket = new DatagramSocket(); // Cria o socket sem especificar a porta
        } catch (SocketException ex) {
            Logger.getLogger(EnviaDados.class.getName()).log(Level.SEVERE, null, ex);
        }
        for(int i = 0; i < windowSize + 1; i++){
            bufferDados.add(null);
        }
    }

    public String getFuncao() {
        return funcao;
    }

    private void reenviaPct(int id){
        Timer timer = timers.get(id);
        byte[] buffer = bufferDados.get(id);

        try {
            InetAddress address = InetAddress.getByName("192.168.15.136");
            DatagramPacket packet = new DatagramPacket(
                    buffer, buffer.length, address, portaDestino);

            System.out.println(BLUE + "Re-enviando pacote " + id);
            datagramSocket.send(packet);
            startTimer(id);

        } catch (IOException ex) {
            Logger.getLogger(EnviaDados.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void enviaPct(int seqnum, int[] dados) {
        //converte int[] para byte[]
        ByteBuffer byteBuffer = ByteBuffer.allocate(4+(dados.length * 4));
        IntBuffer intBuffer = byteBuffer.asIntBuffer();
        intBuffer.put(seqnum);
        intBuffer.put(dados);

        byte[] buffer = byteBuffer.array();
        if(bufferDados.get(seqnum) == null) bufferDados.add(seqnum,buffer);
        else bufferDados.set(seqnum, buffer);

        try {
            //System.out.println("Semaforo: " + sem.availablePermits());
            sem.acquire();
            //System.out.println("Semaforo: " + sem.availablePermits());

            InetAddress address = InetAddress.getByName("192.168.15.136");
            try (DatagramSocket datagramSocket = new DatagramSocket(portaLocalEnvio)) {
                DatagramPacket packet = new DatagramPacket(
                        buffer, buffer.length, address, portaDestino);

                System.out.println(BLUE + "Enviando pacote " + seqnum);
                datagramSocket.send(packet);
                startTimer(seqnum);
            }

        } catch (SocketException ex) {
            Logger.getLogger(EnviaDados.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(EnviaDados.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void run() {
        switch (this.getFuncao()) {
            case "envia":
                System.out.println(BLUE + "Protocolo inicializado com janela de tamanho " + windowSize);

                //variavel onde os dados lidos serao gravados
                int[] dados = new int[350];
                //contador, para gerar pacotes com 1400 Bytes de tamanho
                //como cada int ocupa 4 Bytes, estamos lendo blocos com 350
                //int's por vez.
                int cont = 0;
                //int para determinar sequencia de enumeração
                int seqnum = 0;

                try (FileInputStream fileInput = new FileInputStream("entrada3.jpg");) {
                    int lido;
                    while ((lido = fileInput.read()) != -1) {
                        dados[cont] = lido;
                        cont++;
                        if (cont == 350) {
                            //envia pacotes a cada 350 int's lidos.
                            //ou seja, 1400 Bytes.
                            enviaPct(seqnum, dados);
                            if(seqnum >= windowSize) seqnum = 0;
                            else seqnum++;
                            cont = 0;
                        }
                    }

                    //ultimo pacote eh preenchido com
                    //-1 ate o fim, indicando que acabou
                    //o envio dos dados.
                    for (int i = cont; i < 350; i++)
                        dados[i] = -1;
                    enviaPct(seqnum, dados);

                } catch (IOException e) {
                    System.out.println("Error message: " + e.getMessage());
                }
                break;
            case "ack":
                try {
                    DatagramSocket serverSocket = new DatagramSocket(portaLocalRecebimento);
                    byte[] receiveData = new byte[4];
                    int retorno = 0;
                    while (retorno != -1) {
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        serverSocket.receive(receivePacket);
                        retorno = ((receiveData[0] & 0xff) << 24) +
                                  ((receiveData[1] & 0xff) << 16) +
                                  ((receiveData[2] & 0xff) << 8) +
                                  ((receiveData[3] & 0xff));
                        System.out.println(BLUE + "ACK " + retorno + " recebido");
                        if(retorno != -1) stopTimer(retorno);
                        else {
                            for(int i = 0; i < timers.size(); i++){
                                stopTimer(i);
                            }
                        }
                        sem.release();
                    }
                    System.out.println(BLUE + "Operacao completa.");
                    //System.exit(0);

                } catch (IOException e) {
                    System.out.println("Excecao: " + e.getMessage());
                }
                break;
            default:
                break;
        }
    }
}
