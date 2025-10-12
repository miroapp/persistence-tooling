package com.miro.persistence.tooling.core;

import java.util.List;

/**
 * @author Nikolai Averin
 * @author Ignat Nikitenko
 * @author Sergey Chernov
 * @author Konstantin Subbotin
 */
public interface PostgresExecutable {

    /**
     * Starts postgres, executes scripts from {@code initScripts} and returns {@code jdbcUrl} of the postgres
     */
    String start(String dbName, String user, String password, List<InitScript> initScripts);

    /**
     * Saves state of postgres with associated name.
     */
    void saveState(String imageName, String tag);

    void stop();

    String getBaseImageName();
}
