/**
 * Created by chasrickarby on 9/19/16.
 */
public class Location {
    private double latitude, longitude;
    private String date, time;

    public Location(String[] data){
        date = data[0];
        time = data[1];
        latitude = Double.parseDouble(data[2]);
        longitude = Double.parseDouble(data[3]);
    }
}
