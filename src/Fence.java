import org.openstreetmap.gui.jmapviewer.Coordinate;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by chasrickarby on 9/26/16.
 */
public class Fence {
    public Coordinate location;
    public Double radius;

    public Fence(Coordinate loc, Double r){
        location = new Coordinate(loc.getLat(), loc.getLon());
        radius = r;
    }
}
