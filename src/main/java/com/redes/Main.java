package com.redes;
import java.util.ArrayList;
import java.util.Timer;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    public static void main(String[] args) {

        int windowSize = 4;
        ArrayList<Timer> timers = new ArrayList<>();
        for(int i = 0; i < windowSize + 1; i++){
            timers.add(null);
        }

//        RecebeDados rd = new RecebeDados(windowSize);
//        rd.start();

        Semaphore sem = new Semaphore(windowSize);
        EnviaDados ed1 = new EnviaDados(sem, "envia", windowSize, timers);
        EnviaDados ed2 = new EnviaDados(sem, "ack",windowSize, timers);

        ed2.start();
        ed1.start();

        try {
            ed1.join();
            ed2.join();

            //rd.join();
        } catch (InterruptedException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}