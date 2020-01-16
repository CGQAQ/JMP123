package com.cg;

import jmp123.PlayBack;
import jmp123.output.Audio;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        Audio audio = new Audio();
        PlayBack playBack = new PlayBack(audio);
        try {
            playBack.open("/home/jason/Downloads/Ed Sheeran - I Don't Care (Live At Abbey Road).mp3", "I don't care");
            playBack.start(true);
        }  catch (IOException e) {
            e.printStackTrace();
        }

    }
}
