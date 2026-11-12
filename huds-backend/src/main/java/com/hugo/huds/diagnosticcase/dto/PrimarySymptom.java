package com.hugo.huds.diagnosticcase.dto;

/**
 *  * Primary symptom on the diagnostic form: the root decision branch (one of seven).
 *  *
 *  * <p>The values must match the CHECK constraint on {@code diagnostic_cases.primary_symptom}.</p>
 *
 * @author Meiyan Wang
 * @date 2026-11-10 10:33 a.m.
 */
public enum PrimarySymptom {
    NO_POWER,
    NO_START,
    NO_HEAT_COOL,
    LEAK_DRAIN,
    NOISE_VIBRATION,
    STOPS_MID_CYCLE,
    INTERMITTENT
}
