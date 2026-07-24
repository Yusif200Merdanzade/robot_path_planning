package com.example.robotPath.fuzzy;

/**
 * Input linguistic variable: facial expression, in range [0, 100].
 * Each constant owns its own trapezoid/triangle membership function.
 */
public enum Expression {

    HAPPY {
        @Override
        public double membership(double e) {
            if (e <= 20) return 1;
            if (e <= 25) return (25 - e) / 5;
            return 0;
        }
    },
    SURPRISE {
        @Override
        public double membership(double e) {
            if (e <= 20) return 0;
            if (e <= 25) return (e - 20) / 5;
            if (e <= 40) return 1;
            if (e <= 50) return (50 - e) / 10;
            return 0;
        }
    },
    FEAR {
        @Override
        public double membership(double e) {
            if (e <= 40) return 0;
            if (e <= 50) return (e - 40) / 10;
            if (e <= 60) return 1;
            if (e <= 70) return (70 - e) / 10;
            return 0;
        }
    },
    ANGER {
        @Override
        public double membership(double e) {
            if (e <= 60) return 0;
            if (e <= 70) return (e - 60) / 10;
            if (e <= 80) return 1;
            if (e <= 85) return (85 - e) / 5;
            return 0;
        }
    },
    DISGUST {
        @Override
        public double membership(double e) {
            if (e <= 80) return 0;
            if (e <= 85) return (e - 80) / 5;
            return 1;
        }
    };

    public abstract double membership(double e);
}
