package com.rcs.ssf.dynamic;

import oracle.jdbc.OracleConnection;
import java.sql.Array;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Struct;
import java.util.List;

final class OracleArrayUtils {

    private OracleArrayUtils() {
    }

    static Array toVarcharArray(Connection connection, String sqlTypeName, List<String> values) throws SQLException {
        OracleConnection oracleConnection = connection.unwrap(OracleConnection.class);
        Object[] data = values != null ? values.toArray() : new Object[0];
        return oracleConnection.createARRAY(sqlTypeName, data);
    }

    static Array toStructArray(Connection connection, String arrayTypeName, String structTypeName, List<Object[]> attributes) throws SQLException {
        OracleConnection oracleConnection = connection.unwrap(OracleConnection.class);
        Object[] structs;
        if (attributes == null || attributes.isEmpty()) {
            structs = new Object[0];
        } else {
            Struct[] structArray = new Struct[attributes.size()];
            for (int i = 0; i < attributes.size(); i++) {
                structArray[i] = oracleConnection.createStruct(structTypeName, attributes.get(i));
            }
            structs = structArray;
        }
        return oracleConnection.createARRAY(arrayTypeName, structs);
    }
}
