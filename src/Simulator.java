// License: GPL. For details, see Readme.txt file.

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Cursor;
import java.io.*;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.apache.commons.io.FilenameUtils;
import org.openstreetmap.gui.jmapviewer.*;
import org.openstreetmap.gui.jmapviewer.events.JMVCommandEvent;
import org.openstreetmap.gui.jmapviewer.interfaces.*;

import org.apache.http.*;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.commons.io.IOUtils;

import static java.lang.Math.*;

/**
 * Demonstrates the usage of {@link JMapViewer}
 *
 * @author Jan Peter Stotz
 */
public class Simulator extends JFrame implements JMapViewerEventListener {

    private static final long serialVersionUID = 1L;

    private JMapViewerTree treeMap;

    private JLabel zoomLabel;
    private JLabel zoomValue;

    private JLabel mperpLabelName;
    private JLabel mperpLabelValue;

    private static DataSet dataset;
    private static ArrayList<MapMarker> fenceLocations;
    private Layer personOne;
    private JLabel helpLabel;

    private static int cliNumberFences;
    private static int cliNumberIterations;
    private static String cliLog;
    private static ArrayList<Coordinate> cliFenceLocations;
    private static DataSet datasetCli;

    // For Results:
    static int unrequestedPoints = 0;
    static int requestedPoints = 0;
    static int totalNumberFences = 0;
    static int requestedHits = 0;
    static int requestedMisses = 0;
    static long duration = 0;

    static int batchUnrequestedPoints = 0;
    static int batchRequestedPoints = 0;
    static int batchTotalNumberFences = 0;
    static int batchRequestedHits = 0;
    static int batchRequestedMisses = 0;
    static long batchDuration = 0;
    static int batchRuns = 0;
    static int batchUnHitFences = 0;

    static PrintStream stdout = System.out;

    static boolean batchProcessing = false;

    /**
     * Constructs the {@code Simulator}.
     */
    public Simulator() throws IOException {

        super("Geofence Simulator");
        init();

        AddMouseListeners();
    }

    private void AddMouseListeners() {
        map().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    map().getAttribution().handleAttribution(e.getPoint(), true);
                }
            }
        });

        map().addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Point p = e.getPoint();
                boolean cursorHand = map().getAttribution().handleAttributionCursor(p);
                if (cursorHand) {
                    map().setCursor(new Cursor(Cursor.HAND_CURSOR));
                } else {
                    map().setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                }
            }
        });
    }

    private double GetDistanceToClosestFence(Coordinate curLoc, ArrayList<MapMarker> fenceLocs){
        ArrayList<Double> distances = new ArrayList<>();
        for (MapMarker fence: fenceLocs) {
            distances.add(getDistance(curLoc, fence.getCoordinate()));
        }
        return Collections.min(distances);
    }

    private DataSet GetGPSData(String filePath) {

        System.out.println("Attempting to read in data...");

        DataSet ds = null;
        try {
            dataset = new DataSet(filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Read data in correctly.");

        return dataset;
    }

    private static int GetRoutedTravelTime(Coordinate start, Coordinate end){
        DefaultHttpClient httpclient = new DefaultHttpClient();
        try {
            // specify the get request
            HttpGet getRequest = new HttpGet("http://dev.virtualearth.net/REST/V1/Routes/Driving" +
                    "?wp.0=" + start.getLat() + "," + start.getLon() +
                    "&wp.1=" + end.getLat() + "," + end.getLon() +
                    "&ra=routeSummariesOnly" +
                    "&key=Ah2BJh4cdLWewXKf-u5I98pNrwtZz6JJxfCnbC-5M4GTBeHKDbQdzxtOP8yypEmU");

            HttpResponse httpResponse = httpclient.execute(getRequest);
            HttpEntity entity = httpResponse.getEntity();

            if (entity != null) {
                return GetTravelTime(entity);
            }else{
                JOptionPane.showMessageDialog(null, "Something went wrong, could not request route information",
                        "InfoBox: " + "Uh Oh.", JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // When HttpClient instance is no longer needed,
            // shut down the connection manager to ensure
            // immediate deallocation of all system resources
            httpclient.getConnectionManager().shutdown();
        }
        return 0;
    }

    /*
    Based on a REST response gets the travel time (using routing) between two points, in seconds.
     */
    private static int GetTravelTime(HttpEntity response) throws IOException {
        StringWriter writer=new StringWriter();
        IOUtils.copy(response.getContent(),writer);
        String responseStr = writer.toString();
        Pattern p = Pattern.compile("\"travelDuration\":[0-9]+,");
        Matcher m = p.matcher(responseStr);
        m.find();
        String requiredData = m.group();

        p = Pattern.compile("[0-9]+");
        m = p.matcher(requiredData);
        m.find();
        int n = Integer.parseInt(m.group());
        return n;
    }

    private void init() {
        setSize(400, 400);

        treeMap = new JMapViewerTree("Zones");

        // Listen to the map viewer for user operations so components will
        // receive events and update
        map().addJMVListener(this);

        setLayout(new BorderLayout());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        JPanel panel = new JPanel(new BorderLayout());
        JPanel panelTop = new JPanel();
        JPanel panelBottom = new JPanel();
        JPanel helpPanel = new JPanel();

        mperpLabelName = new JLabel("Meters/Pixels: ");
        mperpLabelValue = new JLabel(String.format("%s", map().getMeterPerPixel()));

        zoomLabel = new JLabel("Zoom: ");
        zoomValue = new JLabel(String.format("%s", map().getZoom()));

        add(panel, BorderLayout.NORTH);
        add(helpPanel, BorderLayout.SOUTH);
        panel.add(panelTop, BorderLayout.NORTH);
        panel.add(panelBottom, BorderLayout.SOUTH);
        helpLabel = new JLabel("Use right mouse button to move,\n "
                + "left double click or mouse wheel to zoom.");
        helpPanel.add(helpLabel);
        JButton button = new JButton("Fit GPS Markers");
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                map().setDisplayToFitMapMarkers();
            }
        });
        panelBottom.add(button);

        JButton openButton = new JButton("Open GPS File");
        openButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e){
                JFileChooser openFileDlg = new JFileChooser();
                FileNameExtensionFilter filter = new FileNameExtensionFilter("TEXT FILES", "txt", "text");
                openFileDlg.setFileFilter(filter);

                if (openFileDlg.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    File file = openFileDlg.getSelectedFile();
                    //This is where a real application would open the file.
                    System.out.println("Opening: " + file.getAbsolutePath() + ".");
                    plotPoints(file.getAbsolutePath());
                    plotFirstPoint();
                } else {
                    System.out.println("Open command cancelled by user.");
                }
            }
        });
        panelBottom.add(openButton);

        JButton plotNextPointButton = new JButton("Plot Next Point");
        plotNextPointButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(plotAPoint() == 1){
                    return;
                }
            }
        });
        panelBottom.add(plotNextPointButton);

        JButton plotRemainingButton = new JButton("Plot All/Remaining Points");
        plotRemainingButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e){
                long startTime = System.currentTimeMillis();
                while(dataset.data.size() > 0 && fenceLocations.size() > 0){
                    if(plotAPoint() == 1){
                        return;
                    }
                }
                long endTime = System.currentTimeMillis();
                duration = (endTime - startTime);  //divide by 1000000 to get milliseconds.
                    printResults();
            }
        });
        panelBottom.add(plotRemainingButton);

        final JCheckBox showMapMarker = new JCheckBox("Map markers visible");
        showMapMarker.setSelected(map().getMapMarkersVisible());
        showMapMarker.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                map().setMapMarkerVisible(showMapMarker.isSelected());
            }
        });
        panelBottom.add(showMapMarker);


        panelTop.add(zoomLabel);
        panelTop.add(zoomValue);
        panelTop.add(mperpLabelName);
        panelTop.add(mperpLabelValue);

        add(treeMap, BorderLayout.CENTER);
    }

    /**
     * @param miles Distance in miles to be converted to a double representing OSM radius distance.
     */
    private double convertMilesToOSM(double miles){
        return miles/69;
    }

    private JMapViewer map() {
        return treeMap.getViewer();
    }

    private static Coordinate c(double lat, double lon) {
        return new Coordinate(lat, lon);
    }

    /**
     * @param args Main program arguments
     */
    public static void main(String[] args) throws IOException {
        if(args.length > 0){

            boolean done = false;
            boolean isFile = false;
            // Arguments: <file or folder> <number of fences> <number of iterations> <log file>

            // Run in command line
            // Check for .txt
            // Check for number of fences
            //      If no argument, do 10 random fences, do not allow over 30
            // Check for iteration
            //      If no argument, do 1 iteration
            // Check for log location
            //      If no argument, just put a log.txt in the same directory as the data files
            // print results for every iteration before moving on to next file
            while(!done){
                if (args[0].equals("-h")){
                    System.out.println("Arguments: <file or folder> <number of fences> <number of iterations (for a single file only)> <log file>");
                }else{
                    done = true;
                    if(args[0].contains(".txt")){
                        isFile = true;
                    }else{
                        isFile = false;
                    }
                    cliNumberFences = Integer.parseInt(args[1]);
                    cliNumberIterations = Integer.parseInt(args[2]);
                    if(!args[3].contains(".txt"))
                    {
                        cliLog = args[3] + "_" + args[0] + "_" + cliNumberFences + "Fences"  + ".txt";
                    }else{
                        cliLog = args[3].replace(".txt", "");
                        String fileName = args[0].replace(".txt", "");
                        fileName = fileName.replace("/", "-");
                        cliLog = cliLog + "_" + fileName + "_" + cliNumberFences + "Fences_" + cliNumberIterations + "Iterations.txt";
                    }

                    File logFile = new File(cliLog);

                    String baseName = FilenameUtils.getBaseName( logFile.getName() );
                    String extension = FilenameUtils.getExtension( logFile.getName() );
                    int counter = 1;
                    while(logFile.exists())
                    {
                        logFile = new File( logFile.getParent(), baseName + "-" + (counter++) + "." + extension );
                    }
                    cliLog = logFile.getName();

                    System.setOut(new PrintStream(cliLog));

                    File f = new File(args[0]);

                    if(isFile){
                        if(cliNumberIterations > 1){
                            batchProcessing = true;
                        }

                        for (int i = 0; i < cliNumberIterations; i++){
                            System.out.println(f.getAbsolutePath());
                            datasetCli = new DataSet(f.getAbsolutePath());
                            runCLI(cliNumberFences);
                        }
                    }else{
                        batchProcessing = true;
                        FilenameFilter textFilter = new FilenameFilter() {
                            public boolean accept(File dir, String name) {
                                return name.toLowerCase().endsWith(".txt");
                            }
                        };

                        File[] files = f.listFiles(textFilter);
                        for (File file : files) {
                            if (file.isDirectory()) {
                                System.out.print("directory:");
                            } else {
                                System.out.print("     file:");
                            }
                            System.out.println(file.getCanonicalPath());
                        }
                    }


                }
            }


        }else{
            new Simulator().setVisible(true);
        }
    }

    private static void runCLI(int numFences) throws FileNotFoundException {
        
        Random random = new Random(System.currentTimeMillis());

        cliFenceLocations = new ArrayList<>();
        totalNumberFences = numFences;

        for (int i = 0; i < numFences; i++) {
            int index = random.nextInt(datasetCli.data.size());

            // Make sure none of the fences overlap
            if(cliFenceLocations.size() > 0){
                while (GetDistanceToClosestFenceFromCoords(datasetCli.data.get(index), cliFenceLocations) < 340){
                    index = random.nextInt(datasetCli.data.size());
                }
            }

            cliFenceLocations.add(datasetCli.data.get(index));
        }

        long startTime = System.currentTimeMillis();
        while(datasetCli.data.size() > 0 && cliFenceLocations.size() > 0){
            if(plotAPointCli() == 1){
                return;
            }
        }
        long endTime = System.currentTimeMillis();
        duration = (endTime - startTime);  //divide by 1000000 to get milliseconds.
        printResults();
    }

    private static Double GetDistanceToClosestFenceFromCoords(Coordinate coordinate, ArrayList<Coordinate> fenceLocs) {
        ArrayList<Double> distances = new ArrayList<>();
        for (Coordinate fence: fenceLocs) {
            distances.add(getDistance(coordinate, fence));
        }
        return Collections.min(distances);
    }


    /*
     * Calculate distance between two points in latitude and longitude taking
     * into account height difference. If you are not interested in height
     * difference pass 0.0. Uses Haversine method as its base.
     *
     * lat1, lon1 Start point lat2, lon2 End point el1 Start altitude in meters
     * el2 End altitude in meters
     * @returns Distance in Meters
     */
    private static double distance(double lat1, double lat2, double lon1,
                                  double lon2, double el1, double el2) {

        final int R = 6371; // Radius of the earth

        Double latDistance = toRadians(lat2 - lat1);
        Double lonDistance = toRadians(lon2 - lon1);
        Double a = sin(latDistance / 2) * sin(latDistance / 2)
                + cos(toRadians(lat1)) * cos(toRadians(lat2))
                * sin(lonDistance / 2) * sin(lonDistance / 2);
        Double c = 2 * atan2(sqrt(a), sqrt(1 - a));
        double distance = R * c * 1000; // convert to meters

        double height = el1 - el2;

        distance = pow(distance, 2) + pow(height, 2);

        return sqrt(distance);
    }

    /*
     * Wrapper for the Haversine method.
     *
     * coord1, coord2, Points to get the distance between
     * el2 End altitude in meters
     * @returns Distance in Meters
     */
    private static double getDistance(Coordinate coord1, Coordinate coord2){
        return distance(coord1.getLat(), coord2.getLat(), coord1.getLon(),coord2.getLon(), 0, 0);
    }

    private void plotPoints(String filePath){
        DataSet ds = GetGPSData(filePath);
        dataset = ds;

        personOne = new Layer("Person One");
        Random random = new Random(System.currentTimeMillis());

        fenceLocations = new ArrayList<>(map().getMapMarkerList());

        for (int i = 0; i < ds.data.size()*.001; i++) {
            fenceLocations = new ArrayList<>(map().getMapMarkerList());
            int index = random.nextInt(ds.data.size());

            // Make sure none of the fences overlap
            if(fenceLocations.size() > 0){
                while (GetDistanceToClosestFence(ds.data.get(index), fenceLocations) < 340){
                    index = random.nextInt(ds.data.size());
                }
            }

            map().addMapMarker(new MapMarkerCircle(personOne, new Coordinate(ds.data.get(index).getLat(),
                    ds.data.get(index).getLon()), convertMilesToOSM(0.06)));
        }

        fenceLocations = new ArrayList<>(map().getMapMarkerList());
        totalNumberFences = fenceLocations.size();
    }

    private Coordinate GetClosestFenceCoordinate(Coordinate coordinate, ArrayList<MapMarker> fenceLocs) {
        MapMarker closestRoutedFence = GetClosestRoutedFence(coordinate, fenceLocations);

        return new Coordinate(closestRoutedFence.getCoordinate().getLat(),closestRoutedFence.getCoordinate().getLon());
    }

    private static Coordinate GetClosestFenceCoordinateFromCoordinates(Coordinate coordinate, ArrayList<Coordinate> fenceLocs) {
        Coordinate closestRoutedFence = GetClosestRoutedFenceFromCoordinates(coordinate, fenceLocs);

        return closestRoutedFence;
    }

    private static Coordinate GetClosestRoutedFenceFromCoordinates(Coordinate loc, ArrayList<Coordinate> fenceLocations) {
        Coordinate closestRoutedFence = fenceLocations.get(0);
        int minimumTravelTime = GetRoutedTravelTime(loc, closestRoutedFence);
        for (Coordinate fence:fenceLocations) {
            int curTravelTime = GetRoutedTravelTime(loc, fence);
            if(curTravelTime < minimumTravelTime){
                minimumTravelTime = curTravelTime;
                closestRoutedFence = fence;
            }
        }
        return closestRoutedFence;
    }

    private void updateZoomParameters() {
        if (mperpLabelValue != null)
            mperpLabelValue.setText(String.format("%s", map().getMeterPerPixel()));
        if (zoomValue != null)
            zoomValue.setText(String.format("%s", map().getZoom()));
    }

    private static int plotAPointCli(){
        int index = 0;
        if( datasetCli.data.size() == 0){
            return 1;
        }
        Coordinate loc = datasetCli.data.get(index);
        if(loc == null){
            return 1;
        }
        Coordinate closestFenceLocation = GetClosestFenceCoordinateFromCoordinates(loc, cliFenceLocations);
        int routedTime = GetRoutedTravelTime(loc, closestFenceLocation);
        int marginOfError = (int)(routedTime * 0.25);

        LocalDateTime newTime = LocalDateTime.from(loc.time.plusSeconds(routedTime - marginOfError));

        try{
            while(datasetCli.data.get(index).time.isBefore(newTime)){
                loc = datasetCli.data.get(index);
                unrequestedPoints++;
                datasetCli.data.remove(index);
            }
        }catch(IndexOutOfBoundsException ex){
            return 1;
        }

        loc = datasetCli.data.get(index);
        routedTime = GetRoutedTravelTime(loc, closestFenceLocation);
        if(routedTime < 30){
            for (int i = 0; i < cliNumberFences; i++){
                if(cliFenceLocations.get(i).getLat() == closestFenceLocation.getLat() && cliFenceLocations.get(i).getLon() == closestFenceLocation.getLon()){
                    cliFenceLocations.remove(i);
                    break;
                }
            }
            requestedPoints++;
            requestedHits++;
        }else{
            requestedPoints++;
            requestedMisses++;
        }

        datasetCli.data.remove(index);
        return 0;
    }

    private int plotAPoint(){
        int index = 0;
        if(dataset.data.size() == 0){
            return 1;
        }
        Coordinate loc = dataset.data.get(index);
        if(loc == null){
            return 1;
        }
        //double closestFenceDistance = GetDistanceToClosestFence(loc,fenceLocations);
        Coordinate closestFenceLocation = GetClosestFenceCoordinate(loc, fenceLocations);
        int routedTime = GetRoutedTravelTime(loc, closestFenceLocation);
        int marginOfError = (int)(routedTime * 0.25);

        LocalDateTime newTime = LocalDateTime.from(loc.time.plusSeconds(routedTime - marginOfError));

        try{
            while(dataset.data.get(index).time.isBefore(newTime)){
                loc = dataset.data.get(index);
                MapMarkerDot newDot = new MapMarkerDot(Color.RED, loc.getLat(), loc.getLon());
                map().addMapMarker(newDot);
                unrequestedPoints++;
                dataset.data.remove(index);
            }
        }catch(IndexOutOfBoundsException ex){
            return 1;
        }

        loc = dataset.data.get(index);
        routedTime = GetRoutedTravelTime(loc, closestFenceLocation);
        if(routedTime < 30){
            MapMarkerDot newDot = new MapMarkerDot(Color.GREEN, loc.getLat(), loc.getLon());
            map().addMapMarker(newDot);
            helpLabel.setText("Geofence Hit: " + loc.time );
            System.out.println("Before: " + fenceLocations.size());
            for (int i = 0; i < map().getMapMarkerList().size(); i++){
                if(fenceLocations.get(i).getCoordinate().getLat() == closestFenceLocation.getLat() && fenceLocations.get(i).getCoordinate().getLon() == closestFenceLocation.getLon()){
                    fenceLocations.remove(i);
                    break;
                }
            }
            requestedPoints++;
            requestedHits++;
            System.out.println("After: " + fenceLocations.size());
        }else{
            MapMarkerDot newDot = new MapMarkerDot(personOne, null, loc.getLat(), loc.getLon());
            map().addMapMarker(newDot);
            helpLabel.setText("Current Time: " + loc.time );
            requestedPoints++;
            requestedMisses++;
        }

        dataset.data.remove(index);
        return 0;
    }

    private static void printResults(){
        System.out.println("\n\n- - - - - - - - - - - - - Results from this run - - - - - - - - - - - - -\n\n");
        batchRuns++;
        System.out.println("GPS points requested:\t" + requestedPoints);
        batchRequestedPoints += requestedPoints;
        System.out.println("GPS points skipped:\t" + unrequestedPoints);
        batchUnrequestedPoints += unrequestedPoints;
        System.out.println("Total number of GPS points:\t" + (requestedPoints + unrequestedPoints));
        System.out.println("Total number of fences:\t" + totalNumberFences);
        batchTotalNumberFences += totalNumberFences;
        int unHitFences = cliFenceLocations.size();
        batchUnHitFences += cliFenceLocations.size();
        int hitFences = totalNumberFences - unHitFences;
        double efficiency = 100.00 - (((double)requestedPoints/((double)requestedPoints + (double)unrequestedPoints))*100.00);
        double accuracy = ((double)hitFences/(double)totalNumberFences) * 100.00;
        System.out.println("Unhit fences:\t" + unHitFences);
        System.out.println("Accuracy:\t" + accuracy + "%");
        System.out.println("Efficiency:\t" + efficiency + "%");

        SimpleDateFormat df = new SimpleDateFormat("HH 'hours', mm 'mins,' ss 'seconds'");
        df.setTimeZone(TimeZone.getTimeZone("GMT+0"));
        batchDuration += duration;
        System.out.println("Execution Time:\t" + getDurationBreakdown(duration));

        if(batchProcessing){
            System.out.println("\n\n- - - - - - - - - - - - - Batch results so far - - - - - - - - - - - - -\n\n");
            System.out.println("GPS points requested:\t" + batchRequestedPoints);
            System.out.println("GPS points skipped:\t" + batchUnrequestedPoints);
            System.out.println("Total number of GPS points:\t" + (batchRequestedPoints + batchUnrequestedPoints));
            System.out.println("Total number of fences:\t" + totalNumberFences);
            int batchHitFences = batchTotalNumberFences - batchUnHitFences;
            double batchEfficiency = 100.00 - (((double)batchRequestedPoints/((double)batchRequestedPoints + (double)batchUnrequestedPoints))*100.00);
            double batchAccuracy = ((double)batchHitFences/(double)batchTotalNumberFences) * 100.00;
            System.out.println("Unhit fences:\t" + batchUnHitFences);
            System.out.println("Average Accuracy:\t" + batchAccuracy + "%");
            System.out.println("Average Efficiency:\t" + batchEfficiency + "%");

            SimpleDateFormat batchDf = new SimpleDateFormat("HH 'hours', mm 'mins,' ss 'seconds'");
            df.setTimeZone(TimeZone.getTimeZone("GMT+0"));
            System.out.println("Total Execution Time:\t" + getDurationBreakdown(batchDuration));
        }
    }

    /**
     * Convert a millisecond duration to a string format
     *
     * @param millis A duration to convert to a string form
     * @return A string of the form "X Days Y Hours Z Minutes A Seconds B Milliseconds".
     */
    public static String getDurationBreakdown(long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("Duration must be greater than zero!");
        }

        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        long milliseconds = millis % 1000;

        return String.format("%d Days %d Hours %d Minutes %d Seconds %d Milliseconds",
                days, hours, minutes, seconds, milliseconds);
    }

    private MapMarker GetClosestRoutedFence(Coordinate loc, ArrayList<MapMarker> fenceLocations) {
        MapMarker closestRoutedFence = fenceLocations.get(0);
        int minimumTravelTime = GetRoutedTravelTime(loc, closestRoutedFence.getCoordinate());
        for (MapMarker fence:fenceLocations) {
            int curTravelTime = GetRoutedTravelTime(loc, fence.getCoordinate());
            if(curTravelTime < minimumTravelTime){
                minimumTravelTime = curTravelTime;
                closestRoutedFence = fence;
            }
        }
        return closestRoutedFence;
    }

    private void plotFirstPoint(){
        Coordinate loc = dataset.data.get(0);
        if(GetDistanceToClosestFence(loc,fenceLocations) < 102){
            MapMarkerDot newDot = new MapMarkerDot(Color.GREEN, loc.getLat(), loc.getLon());
            map().addMapMarker(newDot);
            Coordinate closestFenceLocation = GetClosestFenceCoordinate(loc, fenceLocations);
            helpLabel.setText("Geofence Hit: " + loc.time );
            requestedHits++;
            System.out.println("Before: " + fenceLocations.size());
            for (int i = 0; i < map().getMapMarkerList().size(); i++){
                if(fenceLocations.get(i).getCoordinate().getLat() == closestFenceLocation.getLat() && fenceLocations.get(i).getCoordinate().getLon() == closestFenceLocation.getLon()){
                    fenceLocations.remove(i);
                    break;
                }
            }
            System.out.println("After: " + fenceLocations.size());
        }else{
            MapMarkerDot newDot = new MapMarkerDot(personOne, null, loc.getLat(), loc.getLon());
            map().addMapMarker(newDot);
            helpLabel.setText("Current Time: " + loc.time );
            requestedMisses++;
        }
        dataset.data.remove(0);
        requestedPoints++;
    }

    @Override
    public void processCommand(JMVCommandEvent command) {
        if (command.getCommand().equals(JMVCommandEvent.COMMAND.ZOOM) ||
                command.getCommand().equals(JMVCommandEvent.COMMAND.MOVE)) {
            updateZoomParameters();
        }
    }
}
