package com.parallax.generator;

/**
 * Synthetic history CLI entry point. Commands are implemented in Prompt 12.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.out.println("Parallax data-generator — synthetic history CLI.");
        System.out.println("Usage: java -jar data-generator.jar <command> [options]");
        System.out.println("Commands arrive in Prompt 12.");
    }
}
