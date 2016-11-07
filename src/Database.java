/**
 * Created by chasrickarby on 11/2/16.
 */
import org.openstreetmap.gui.jmapviewer.Coordinate;

import java.sql.*;

public class Database
{

    Connection c;
    String curTable;

    public Database()
    {
        c = null;
        try {
            Class.forName("org.sqlite.JDBC");
            c = DriverManager.getConnection("jdbc:sqlite:/Users/chasrickarby/Development/geofence-sim/geofence-sim.sqlite");
        } catch ( Exception e ) {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() );
            System.exit(0);
        }
        System.out.println("Opened database successfully");
    }

    private void dbCreateTable(String tableName) throws SQLException{
        Statement stmt = null; // statement object
        stmt = c.createStatement();

        // create a table called whatever the current data file is called
        tableName = tableName.replace(".txt", "");
        String sql = "CREATE TABLE " + tableName + " (locStartLat DOUBLE, locStartLon DOUBLE, locEndLat DOUBLE, locEndLon DOUBLE, time DOUBLE)";
        stmt.executeUpdate(sql);
        curTable = tableName;
    }

    public void dbOpenTable(String tableName) throws SQLException{
        tableName = tableName.replace(".txt", "");
        if (dbTableExist(tableName)){
            curTable = tableName;
        }else{
            dbCreateTable(tableName);
        }
    }

    public void dbInsertEntry(String statement) throws SQLException{
        Statement stmt = c.createStatement();
        stmt.executeUpdate("INSERT INTO " + curTable + " " + statement);
    }

    public boolean dbTableExist(String name) throws SQLException {
        boolean tExists = false;
        try (ResultSet rs = c.getMetaData().getTables(null, null, name, null)) {
            while (rs.next()) {
                String tName = rs.getString("TABLE_NAME");
                if (tName != null && tName.equals(name)) {
                    tExists = true;
                    break;
                }
            }
        }
        return tExists;
    }

    public int executeTravelTimeQuery(Coordinate start, Coordinate end) throws SQLException {
        Statement stmt = c.createStatement();
        ResultSet rs = null; // result set object
        int travelTime = -1;
        String sql = "SELECT * FROM " + curTable + " WHERE (locStartLat=" + start.getLat() + " AND locStartLon=" + start.getLon() + " AND locEndLat=" + end.getLat() + " AND locEndLon=" + end.getLon() + ") OR " +
                "(locStartLat=" + end.getLat() + " AND locStartLon=" + end.getLon() + " AND locEndLat=" + start.getLat() + " AND locEndLon=" + start.getLon() + ")";
        rs = stmt.executeQuery(sql);
        while (rs.next()) {
            travelTime = rs.getInt(5);
        }
        return travelTime;
    }
}
