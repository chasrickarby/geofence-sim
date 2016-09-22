/**
 * Created by chasrickarby on 9/19/16.
 */

import org.openstreetmap.gui.jmapviewer.Coordinate;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DataSet {

    public List<Coordinate> data;

    public DataSet(String filePath) throws IOException{

        data = new ArrayList<>();

        // Construct BufferedReader from FileReader
        BufferedReader br = new BufferedReader(new FileReader(filePath));

        String line = null;
        while ((line = br.readLine()) != null) {
            if(line.contains("Date (month/day/year)")){
                continue;
            }
            String[] curLine = line.split("\\t");
            data.add(new Coordinate(Double.parseDouble(curLine[2]), Double.parseDouble(curLine[3])));
        }

        br.close();
    }
}
