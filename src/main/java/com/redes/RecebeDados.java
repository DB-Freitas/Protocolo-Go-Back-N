/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.redes;

/*
 * @author flavio
 */

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Random;

@SuppressWarnings("ALL")
public class RecebeDados extends Thread {

    private final int portaLocalReceber = 2001;
    private final int portaLocalEnviar = 2002;
    private final int portaDestino = 2003;

    int windowSize;
    String YELLOW = "\u001B[33m";
    String RED = "\u001B[31m";

    public RecebeDados(int windowSize){
        this.windowSize = windowSize;
    }

    private void enviaAck(int seqnum) {
    // vou convencionar que -1 indica fim de pacote, analogo ao antigo F
        try {
            InetAddress address = InetAddress.getByName("localhost");
            try (DatagramSocket datagramSocket = new DatagramSocket(portaLocalEnviar)) {
                ByteBuffer byteBuffer = ByteBuffer.allocate(4);
                IntBuffer intBuffer = byteBuffer.asIntBuffer();
                intBuffer.put(seqnum);
                byte[] sendData = byteBuffer.array();

                DatagramPacket packet = new DatagramPacket(
                        sendData, sendData.length, address, portaDestino);
                System.out.println(YELLOW + "Enviando ACK " + seqnum);
                datagramSocket.send(packet);
            }
        } catch (SocketException ex) {
            Logger.getLogger(RecebeDados.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(RecebeDados.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void run() {
        try {
            DatagramSocket serverSocket = new DatagramSocket(portaLocalReceber);
            byte[] receiveData = new byte[1404];

            // seqnum é o enumerador do pacote recebido.
            // ackSeqnum é o enumerador eh o numero do ack atual do receptor.
            // O pacote esperado eh igual a ackSeqnum + 1.
            // se seqnum =/= ackSeqnum + 1, o pacote está fora de ordem e será descartado.
            // consequentemente ele manda um ack = ackSeqnum para o pacote ser reenviado.
            // vou convencionar que seqnum negativo indica problema, idealmente queremos fugir dele.

            int seqnum = -1;
            int ackSeqnum = 0;

            try (FileOutputStream fileOutput = new FileOutputStream("saida2.png")) {

                Random rand = new Random();
                float failChance = 0;
                boolean dataReceptionSucess = false;

                while (ackSeqnum != -1) {
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    serverSocket.receive(receivePacket);
                    //proximo print eh redundante devido a prints mais pra baixo
                    //System.out.println("dado recebido");
                    byte[] tmp = receivePacket.getData();

                    //probabilidade de 60% de perder
                    //gero um numero aleatorio contido entre [0,1]
                    //se numero cair no intervalo [0, 0,6)
                    //significa perda, logo, você não envia ACK
                    //para esse pacote, e não escreve ele no arquivo saida.
                    //se o numero cair no intervalo [0,6, 1,0]
                    //assume-se o recebimento com sucesso.


                    failChance = rand.nextFloat();
                    //System.out.println(failChance);
                    dataReceptionSucess = failChance > 0.6;
                    //dataReceptionSucess = true; // serve apenas para teste

                    if(dataReceptionSucess) {
                        seqnum = ((tmp[0] & 0xff) << 24) + ((tmp[1] & 0xff) << 16) + ((tmp[2] & 0xff) << 8) + ((tmp[3] & 0xff));

                        //testa se o pacote recebido esta em ordem
                        if(seqnum == ackSeqnum) {
                            System.out.println(YELLOW + "Pacote " + seqnum + " recebido com sucesso");

                            for (int i = 4; i < tmp.length; i = i + 4) {
                                int dados = ((tmp[i] & 0xff) << 24) + ((tmp[i + 1] & 0xff) << 16) + ((tmp[i + 2] & 0xff) << 8) + ((tmp[i + 3] & 0xff));

                                if (dados == -1) {
                                    ackSeqnum = -1;
                                    break;
                                }
                                fileOutput.write(dados);
                            }
                            enviaAck(ackSeqnum);
                            if(ackSeqnum == -1) break;
                            else {
                                if (ackSeqnum >= windowSize) ackSeqnum = 0;
                                else ackSeqnum++;
                            }
                        }
                        else{
                            System.out.println(YELLOW + "Pacote fora de ordem. Esperado: " + ackSeqnum + ", recebido: " + seqnum);
                        }
                    }
                    else{
                        System.out.println(YELLOW + "Falha ao receber dado");
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Excecao: " + e.getMessage());
        }
        currentThread().interrupt();
    }
}
