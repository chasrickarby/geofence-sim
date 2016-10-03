// License: GPL. For details, see Readme.txt file.
package org.openstreetmap.gui.jmapviewer;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Time;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;

import org.openstreetmap.gui.jmapviewer.interfaces.ICoordinate;

/**
 * This class encapsulates a Point2D.Double and provide access
 * via <tt>lat</tt> and <tt>lon</tt>.
 *
 * @author Jan Peter Stotz
 *
 */
public class Coordinate implements ICoordinate {
    private transient Point2D.Double data;

    public LocalDateTime time;

    public Coordinate(double lat, double lon, String hhmmss) {
        data = new Point2D.Double(lon, lat);
        time = parseTime(hhmmss);
        System.out.println(time);
    }

    public Coordinate(double lat, double lon) {
        data = new Point2D.Double(lon, lat);
    }

    private LocalDateTime parseTime(String mmddyyyyhhmmss) {
        boolean isAfterNoon = false;
        if(mmddyyyyhhmmss.contains("PM")){
            isAfterNoon = true;
            mmddyyyyhhmmss = mmddyyyyhhmmss.replace("PM", "");
        }else{
            mmddyyyyhhmmss = mmddyyyyhhmmss.replace("AM", "");
        }
        mmddyyyyhhmmss = mmddyyyyhhmmss.trim();

        LocalDateTime dateTime = null;
        try{
            DateTimeFormatter f = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss");
            dateTime = LocalDateTime.from(f.parse(mmddyyyyhhmmss));
        }catch (java.time.format.DateTimeParseException ex){
            DateTimeFormatter f = DateTimeFormatter.ofPattern("MM/dd/yyyy H:mm:ss");
            dateTime = LocalDateTime.from(f.parse(mmddyyyyhhmmss));
        }

        // Use Military Time
        if(isAfterNoon){
            dateTime = LocalDateTime.from(dateTime.plusHours(12));
        }
        return dateTime;
    }

    @Override
    public double getLat() {
        return data.y;
    }

    @Override
    public void setLat(double lat) {
        data.y = lat;
    }

    @Override
    public double getLon() {
        return data.x;
    }

    @Override
    public void setLon(double lon) {
        data.x = lon;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeObject(data.x);
        out.writeObject(data.y);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        data = new Point2D.Double();
        data.x = (Double) in.readObject();
        data.y = (Double) in.readObject();
    }

    @Override
    public String toString() {
        return "Coordinate[" + data.y + ", " + data.x + ']';
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 61 * hash + Objects.hashCode(this.data);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Coordinate other = (Coordinate) obj;
        if (!Objects.equals(this.data, other.data)) {
            return false;
        }
        return true;
    }
}
