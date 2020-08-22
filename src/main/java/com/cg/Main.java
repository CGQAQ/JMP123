package com.cg;

import jmp123.PlayBack;
import jmp123.output.Audio;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        Audio audio = new Audio();
        PlayBack playBack = new PlayBack(audio);
        try {
            playBack.open("C:\\Users\\mjaso\\Downloads\\Jason Derulo - Take You Dancing.mp3", "Take you Dancing");
            playBack.start(true);
        }  catch (IOException e) {
            e.printStackTrace();
        }

    }
}
