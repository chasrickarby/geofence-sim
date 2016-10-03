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
    public List<Fence> fences;   // Format: Coordinate, radius

    public DataSet(String filePath) throws IOException{

        data = new ArrayList<>();
        fences = new ArrayList<>();

        // Construct BufferedReader from FileReader
        BufferedReader br = new BufferedReader(new FileReader(filePath));

        String line = null;
        while ((line = br.readLine()) != null) {
            if(line.contains("Date (month/day/year)")){
                continue;
            }else if(line.contains("fences:")){
                getFences(br, fences);
                break;
            }
            String[] curLine = line.split("\\t");
            data.add(new Coordinate(Double.parseDouble(curLine[2]), Double.parseDouble(curLine[3]), curLine[0] + " " + curLine[1]));
        }

        br.close();
    }

    private void getFences(BufferedReader br, List<Fence> fences) throws IOException {
        String line = null;
        while ((line = br.readLine()) != null) {
            String[] curLine = line.split("\\t");
            if(curLine.length < 3){
                return;
            }
            Fence newFence = new Fence(new Coordinate(Double.parseDouble(curLine[0]), Double.parseDouble(curLine[1])), Double.parseDouble(curLine[2]));
            fences.add(newFence);
        }
    }
}
