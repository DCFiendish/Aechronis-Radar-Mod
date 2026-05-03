package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class AechronisRadarMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        System.out.println("[AechronisRadar] Initialized!");
    }
}
