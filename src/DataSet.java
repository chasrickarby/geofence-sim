/**
 * Created by chasrickarby on 9/19/16.
 */

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DataSet {

    public List<Location> data;

    public DataSet(String filePath) throws IOException{

        data = new ArrayList<>();

        // Construct BufferedReader from FileReader
        BufferedReader br = new BufferedReader(new FileReader(filePath));

        String line = null;
        while ((line = br.readLine()) != null) {
            if(line.contains("Date (month/day/year)")){
                continue;
            }
            data.add(new Location(line.split("\\t")));
        }

        br.close();
    }
}
