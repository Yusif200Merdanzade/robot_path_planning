package com.example.robotPath.fuzzy;

/**
 * Input linguistic variable: motion rate, in range [0, 100].
 * Each constant owns its own trapezoid/triangle membership function.
 */
public enum Motion {

    VERY_SLOW {
        @Override
        public double membership(double m) {
            if (m <= 20) return 1;
            if (m <= 25) return (25 - m) / 5;
            return 0;
        }
    },
    SLOW {
        @Override
        public double membership(double m) {
            if (m <= 20) return 0;
            if (m <= 25) return (m - 20) / 5;
            if (m <= 40) return 1;
            if (m <= 50) return (50 - m) / 10;
            return 0;
        }
    },
    NORMAL {
        @Override
        public double membership(double m) {
            if (m <= 40) return 0;
            if (m <= 50) return (m - 40) / 10;
            if (m <= 60) return 1;
            if (m <= 70) return (70 - m) / 10;
            return 0;
        }
    },
    FAST {
        @Override
        public double membership(double m) {
            if (m <= 60) return 0;
            if (m <= 70) return (m - 60) / 10;
            if (m <= 80) return 1;
            if (m <= 85) return (85 - m) / 5;
            return 0;
        }
    },
    VERY_FAST {
        @Override
        public double membership(double m) {
            if (m <= 80) return 0;
            if (m <= 85) return (m - 80) / 5;
            return 1;
        }
    };

    public abstract double membership(double m);
}
