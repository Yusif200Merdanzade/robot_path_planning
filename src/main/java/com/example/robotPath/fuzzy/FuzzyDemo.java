package com.example.robotPath.fuzzy;

import java.util.Scanner;

/**
 * İnteraktiv konsol demosu — facialExpression və motionRate dəyərlərini
 * əl ilə daxil et, sistem attentionLevel-i özü hesablayıb göstərsin.
 * Çıxmaq üçün "q" yaz.
 */
public class FuzzyDemo {

    public static void main(String[] args) {
        MamdaniFuzzyInferenceSystem fis = new MamdaniFuzzyInferenceSystem();
        Scanner scanner = new Scanner(System.in);

        while (true) {
            Double facialExpression = readValue(scanner, "facialExpression (0-100, çıxmaq üçün q): ");
            if (facialExpression == null) {
                break;
            }
            Double motionRate = readValue(scanner, "motionRate (0-100, çıxmaq üçün q): ");
            if (motionRate == null) {
                break;
            }

            FuzzyResult result = fis.infer(facialExpression, motionRate);
            AttentionResponse response = AttentionResponse.from(result);
            System.out.printf("{%n  \"attentionLevel\": \"%s\",%n  \"robotAction\": \"%s\",%n  \"message\": \"%s\"%n}%n%n",
                    response.getAttentionLevel(), response.getRobotAction(), response.getMessage());
        }

        System.out.println("Çıxıldı.");
        scanner.close();
    }

    private static Double readValue(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine().trim();
            if (line.equalsIgnoreCase("q")) {
                return null;
            }
            try {
                double value = Double.parseDouble(line.replace(",", "."));
                if (value < 0 || value > 100) {
                    System.out.println("Xəta: dəyər 0-100 aralığında olmalıdır.");
                    continue;
                }
                return value;
            } catch (NumberFormatException e) {
                System.out.println("Xəta: rəqəm daxil et.");
            }
        }
    }
}
