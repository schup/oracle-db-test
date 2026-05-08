package org.dbtest.connection;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class HostPort {

    private String host;
    private int port;

    @Override
    public String toString() {
        return host + ':' + port;
    }
}
