package com.example.robotpathplanning.fuzzy;

import java.util.Scanner;

/**
 * Fuzzy sistemi yoxlamaq üçün konsol proqramı (Spring-siz, ayrıca işə salınır).
 * İki dəyər daxil edirsən, sistem diqqət səviyyəsini hesablayıb göstərir.
 * Çıxmaq üçün "q" yaz.
 */
public class FuzzyConsoleDemo {

    public static void main(String[] args) {
        MamdaniFuzzyInferenceSystem fis = new MamdaniFuzzyInferenceSystem();
        Scanner in = new Scanner(System.in);
        while (true) {
            Double facial = read(in, "facialExpression (0-100, çıxmaq üçün q): ");
            if (facial == null) break;
            Double motion = read(in, "motionRate (0-100, çıxmaq üçün q): ");
            if (motion == null) break;

            AttentionLevel level = fis.infer(facial, motion);
            System.out.printf("bal=%.1f -> %s (%s): %s%n%n",
                    fis.score(facial, motion), level, level.action, level.message);
        }
        System.out.println("Çıxıldı.");
    }

    /** 0-100 arası rəqəm oxuyur; "q" yazılsa null qaytarır. */
    private static Double read(Scanner in, String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = in.nextLine().trim();
            if (line.equalsIgnoreCase("q")) return null;
            try {
                double v = Double.parseDouble(line.replace(",", "."));
                if (v >= 0 && v <= 100) return v;
                System.out.println("Xəta: dəyər 0-100 aralığında olmalıdır.");
            } catch (NumberFormatException e) {
                System.out.println("Xəta: rəqəm daxil et.");
            }
        }
    }
}
