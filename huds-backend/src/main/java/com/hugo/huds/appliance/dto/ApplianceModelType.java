package com.hugo.huds.appliance.dto;

/**
 * The appliance type a catalog model belongs to.
 * The name of the constant is what is stored in {@code appliance_models.appliance_type}
 *
 * @author Meiyan Wang
 * @date 2026-10-23 9:53 a.m.
 */
public enum ApplianceModelType {
    WASHER,
    DRYER,
    WASHER_DRYER_COMBO,
    REFRIGERATOR,
    FREEZER,
    DISHWASHER,
    RANGE,
    COOKTOP,
    WALL_OVEN,
    MICROWAVE,
    RANGE_HOOD,
    AIR_CONDITIONER,
    DEHUMIDIFIER,
    ICE_MAKER,
    WINE_COOLER,
    TRASH_COMPACTOR,
    OTHER
}
