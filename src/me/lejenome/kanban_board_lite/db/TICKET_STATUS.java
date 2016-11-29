package me.lejenome.kanban_board_lite.db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;

public class TICKET_STATUS {
    private static HashMap<Integer, String> vals;

    public static void initialize() {
        ResultSet res = Connection.executeQuery("Select * From TICKET_STATUS");
        vals = new HashMap<>();
        try {
            while (res.next()) {
                vals.put(res.getInt("level"), res.getString("title"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public static HashMap<Integer, String> getInstance() {
        if (vals == null)
            initialize();
        return vals;
    }
    public static boolean add(int level, String title) {
        boolean res = Connection.execute("Insert INTO TICKET_STATUS (level, title) Values (?, ?)",
                level, Types.SMALLINT,
                title, Types.VARCHAR);
        if (res) {
            vals.put(level, title);
        }
        return res;
    }
    public static boolean delete(int level) {
        boolean res = Connection.execute("DELETE FROM TICKET_STATUS WHERE level = ?",
                level, Types.SMALLINT);
        if (res) {
            vals.remove(level);
        }
        return res;
    }
    public static boolean update(int level, String title) {
        boolean res = Connection.execute("UPDATE TICKET_STATUS SET title = ? where level = ?",
                title, Types.VARCHAR,
                level, Types.SMALLINT);
        if (res) {
            vals.replace(level, title);
        }
        return res;
    }
}
