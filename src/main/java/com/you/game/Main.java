package com.you.game;

public class Main {
    public static void main(String[] args) {
    	System.out.println("GAME PID = " + ProcessHandle.current().pid());
        new Game().run();
    }
}
