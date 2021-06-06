package ru.runa.gpd.lang.model;

import ru.runa.gpd.extension.handler.ParamDef;

// TODO define 3different images for user
public enum BotTaskType {
    /**
     * This bot task has a fixed configuration
     */
    SIMPLE,
    /**
     * This bot task has a fixed configuration in {@link ParamDef} xml format.
     */
    PARAMETERIZED,
    /**
     * This bot task has a dynamic configuration (in server there will be changes to real variables according to task binding parameters)
     */
    EXTENDED,
    /**
     * This bot task has a dynamic configuration, but real variables from Global Section
     */
    GLOBALSECTION
}
