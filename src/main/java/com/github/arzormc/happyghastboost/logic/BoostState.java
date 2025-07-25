package com.github.arzormc.happyghastboost.logic;

import org.bukkit.Particle;

public class BoostState {

    public double charge = 1.0;

    public boolean boosting = false;

    public long boostStartTime = 0L;

    public boolean isHoldingForward = false;

    public long forwardStartTime = 0L;

    public long lastValidForwardTime = 0L;

    public boolean mustReleaseBeforeNextBoost = false;

    public double refillRate = 0.02;

    public double drainRate = 0.00333;

    public Particle particle = Particle.FLAME;

}
