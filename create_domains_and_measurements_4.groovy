import qupath.lib.regions.*
import qupath.lib.gui.measure.ObservableMeasurementTableData
import qupath.lib.gui.tools.MeasurementExporter
import qupath.lib.objects.PathCellObject
import qupath.lib.images.servers.LabeledImageServer

static void main(String[] args) {
    
    def background = getPathClass("Background") 
    setImageType('FLUORESCENCE');
    createAnnotationsFromPixelClassifier("vacuole_boundary_RT_1-8_gauss-laplac_1", 2.0, 2.0, "SPLIT", "INCLUDE_IGNORED")
    //createAnnotationsFromPixelClassifier("vacuole_boundary_RT_1-8_1", 4.0, 4.0, "SPLIT")
    //createAnnotationsFromPixelClassifier("vacuole_boundary_2", 4.0, 4.0, "SPLIT")
    //removes background class annotations, which were used to train the classifier
    removal = getAnnotationObjects().findAll{it.getPathClass().equals(background)}
    removeObjects(removal, true)
    selectAnnotations();
    createAnnotationsFromPixelClassifier("domains_RT_0.5-8scales_gaus_laplac_everywhere", 0.5, 0.5, "SPLIT", "SELECT_NEW")
   
    //Add circularity measurement to all annotations: maybe remove for vacuoles?
    
    
    def ob = new ObservableMeasurementTableData();
    def annotations = getAnnotationObjects()
     // This line creates all the measurements
    ob.setImageData(getCurrentImageData(),  annotations);
    annotations.each { 
        //System.out.println(ob.getAllNames().toString())
        image=ob.getStringValue(it, "Image")
        area=ob.getNumericValue(it, "Area µm^2")
        perimeter=ob.getNumericValue(it, "Perimeter µm")
        circularity = 4*3.14159*area/(perimeter*perimeter) 
        
        it.getMeasurementList().putMeasurement("Area µm^2", area)
        it.getMeasurementList().putMeasurement("Perimeter µm", area)
        it.getMeasurementList().putMeasurement("Circularity", circularity)
        //System.out.println("now " + ob.getAllNames().toString())   
    }
 
    ob.setImageData(getCurrentImageData(),  annotations);
    ArrayList<ArrayList<PathObject> > hierarchyAsList = getDescendentObjectsFromParent(getCurrentImageData().getHierarchy(), "Vacuole");
    //printArraylistOfArraylist(hierarchyAsList);
    ArrayList<PathObject> bestFrameLoDomains = getHighestCircularityFrame(hierarchyAsList, "Lo Domains");
    dirPath = exportAsTSV(bestFrameLoDomains);
    
    createTSVFile(dirPath);
    
}    

//Returns an arraylist of arraylists containing the descendants of each parent object with the specified PathClass
public static List<PathObject> getDescendentObjectsFromParent(final PathObjectHierarchy hierarchy, final String parentClassName) {

  ArrayList<ArrayList<PathObject> > pathObjects = new ArrayList<ArrayList<PathObject> >();
  for (PathObject annotation : hierarchy.getAnnotationObjects()) {
      int i = 0;
      if (parentClassName.equals(annotation.getPathClass().getName())) {
          Collection<PathObject> parentAndDescendants = annotation.getDescendantObjects()
          parentAndDescendants.add(0, annotation);
          pathObjects.add(parentAndDescendants);     
      }
      i++;     
  }
  //System.out.println("all ok");
  printArraylistOfArraylist(pathObjects);
  return pathObjects;
}

//Adapted from: geeksforgeeks.org
//prints the contents of an arraylist of arraylist for debugging
public static printArraylistOfArraylist (ArrayList<ArrayList<PathObject> > aList) {
    for (int i = 0; i < aList.size(); i++) {
            for (int j = 0; j < aList.get(i).size(); j++) {
                System.out.print(aList.get(i).get(j).getPathClass().getName() + " ");
            }
            System.out.println();
    }
}

//Input: arraylist of arraylist and the ROI class that determines what is the best frame
//Returns the z-stack frame with ROIs containing the highest average circularity for further analysis
public static ArrayList<PathObject> getHighestCircularityFrame (ArrayList<ArrayList<PathObject> > listOfList, String roiClassName) {
    double highestAverageCircularity = 0;
    int highestCircularityIndex = 0;
    int numRoi = 0;
    for (int i = 0; i < listOfList.size(); i++) {
        //ArrayList<double> circularitySum = new ArrayList<double>();
        double circularitySum = 0;
        double roiCounter = 0;
        for (int j = 0; j < listOfList.get(i).size(); j++) {
            if (roiClassName.equals(listOfList.get(i).get(j).getPathClass().getName())) {
                roiCounter++;
                circularitySum+=listOfList.get(i).get(j).getMeasurementList().getMeasurementValue("Circularity");
            }
        }
        double averageCircularity = circularitySum/roiCounter;
        if (averageCircularity > highestAverageCircularity) {
            highestAverageCircularity = averageCircularity;
            highestCircularityIndex = i;
            numRoi = roiCounter;
        }
        roiCounter = 0; 
    }
    System.out.println("Highest average circularity: " + highestAverageCircularity);
    System.out.println("With this many relevant ROIs: " + numRoi);
    System.out.println("At index: " + highestCircularityIndex);
    listOfList.get(highestCircularityIndex).get(0).setDescription("Best Vacuole");
    System.out.println(listOfList.get(highestCircularityIndex).get(0).getDescription());
    
    return listOfList.get(highestCircularityIndex);
}

public static String exportAsTSV (ArrayList<PathObject> frame) {

    def name = getProjectEntry().getImageName() + '.txt'
    def path = buildFilePath(PROJECT_BASE_DIR, 'annotation results')
    mkdirs(path)
    path = buildFilePath(path, name)

    StringBuilder builtHeader = new StringBuilder("");
    builtHeader.append("Image").append("\t");
    for (String str : frame.get(0).getMeasurementList().getMeasurementNames()) {
        System.out.println(frame.get(0).getMeasurementList().getMeasurementNames().toString());
        builtHeader.append(str).append("\t");
    }
    String header = builtHeader.toString();
    
    FileWriter writer = new FileWriter(path);
    writer.write(header);
    writer.write("\n");
    for (int i = 0; i < frame.size(); i++) {
        writer.write(getProjectEntry().getImageName() + "\t")
        for (int j = 0; j < frame.get(i).getMeasurementList().getMeasurementNames().size(); j++) {
            String str = frame.get(i).getMeasurementList().getMeasurementValue(j).toString();
            writer.write(str + "\t");
        }
        writer.write("\n");
        /*
        if(i < frame.size()-1) {
            writer.write("\n");
        }
        */
    }
    writer.close();
    return path;    
}

public static void createTSVFile (String dirPath) {

    // Some parameters you might want to change...
    String ext = '.txt' // File extension to search for
    String delimiter = '\t' // Use tab-delimiter (this is for the *input*, not the output)
    String outputName = 'Combined_results.txt' // Name to use for output; use .csv if you really want comma separators
    
    def fileResults = new File(dirPath, outputName)
    
    // Get a list of all the files to merge
    def files = dirPath.listFiles({
        File f -> f.isFile() &&
                f.getName().toLowerCase().endsWith(ext) &&
                f.getName() != outputName} as FileFilter)
    if (files.size() <= 1) {
        print 'At least two results files needed to merge!'
        return
    } else
        print 'Will try to merge ' + files.size() + ' files'
    
    // Represent final results as a 'list of maps'
    def results = new ArrayList<Map<String, String>>()
    
    // Store all column names that we see - not all files necessarily have all columns
    def allColumns = new LinkedHashSet<String>()
    allColumns.add('File name')
    
    // Loop through the files
    for (file in files) {
        // Check if we have anything to read
        def lines = file.readLines()
        if (lines.size() <= 1) {
            print 'No results found in ' + file
            continue
        }
        // Get the header columns
        def iter = lines.iterator()
        def columns = iter.next().split(delimiter)
        allColumns.addAll(columns)
        // Create the entries
        while (iter.hasNext()) {
            def line = iter.next()
            if (line.isEmpty())
                continue
            def map = ['File name': file.getName()]
            def values = line.split(delimiter)
            // Check if we have the expected number of columns
            if (values.size() != columns.size()) {
                print String.format('Number of entries (%d) does not match the number of columns (%d)!', columns.size(), values.size())
                print('I will stop processing ' + file.getName())
                break
            }
            // Store the results
            for (int i = 0; i < columns.size(); i++)
                map[columns[i]] = values[i]
            results.add(map)
        }
    }
    
    // Create a new results file - using a comma delimiter if the extension is csv
    if (outputName.toLowerCase().endsWith('.csv'))
        delimiter = ','
    int count = 0
    fileResults.withPrintWriter {
        def header = String.join(delimiter, allColumns)
        it.println(header)
        // Add each of the results, with blank columns for missing values
        for (result in results) {
            for (column in allColumns) {
                it.print(result.getOrDefault(column, ''))
                it.print(delimiter)
            }
            it.println()
            count++
        }
    }
    
    // Success!  Hopefully...
    print 'Done! ' + count + ' result(s) written to ' + fileResults.getAbsolutePath()
}

public static exportAsCSV (ArrayList<PathObject> frame) {

    def name = getProjectEntry().getImageName() + '.txt'
    def path = buildFilePath(PROJECT_BASE_DIR, 'annotation results')
    mkdirs(path)
    path = buildFilePath(path, name)

    StringBuilder builtHeader = new StringBuilder("");
    builtHeader.append("Image").append(",");
    for (String str : frame.get(0).getMeasurementList().getMeasurementNames()) {
        System.out.println(frame.get(0).getMeasurementList().getMeasurementNames().toString());
        builtHeader.append(str).append(",");
    }
    String header = builtHeader.toString();
    
    FileWriter writer = new FileWriter(path);
    writer.write(header);
    writer.write("\n");
    for (int i = 0; i < frame.size(); i++) {
        writer.write(getProjectEntry().getImageName() + ",")
        for (int j = 0; j < frame.get(i).getMeasurementList().getMeasurementNames().size(); j++) {
            String str = frame.get(i).getMeasurementList().getMeasurementValue(j).toString();
            writer.write(str + ",");
        }
        writer.write("\n");
        /*
        if(i < frame.size()-1) {
            writer.write("\n");
        }
        */
    }
    writer.close();    
}
/*
//Adapted from: Pete Bankhead
public static void exportMeasurements (ArrayList<PathObject> zStackSlice) {
    def imageData = getCurrentImageData()

    // Define output path (relative to project)
    def name = GeneralTools.getNameWithoutExtension(imageData.getServer().getMetadata().getName())
    def pathOutput = buildFilePath(PROJECT_BASE_DIR, 'export', name)
    mkdirs(pathOutput)
    
    // Define output resolution
    double requestedPixelSize = 1.0
    
    // Convert to downsample
    double downsample = requestedPixelSize / imageData.getServer().getPixelCalibration().getAveragedPixelSize()
    
    // Create an ImageServer where the pixels are derived from annotations
    def labelServer = new LabeledImageServer.Builder(imageData)
        .backgroundLabel(0, ColorTools.WHITE) // Specify background label (usually 0 or 255)
        .downsample(downsample)    // Choose server resolution; this should match the resolution at which tiles are exported
        .addLabel('Vacuole', 1) // Choose output labels (the order matters!)
        //.addLabel('Lo Domains', 2)
        //.addLabel('Ld Domains', 3)
        .lineThickness(2)          // Optionally export annotation boundaries with another label
        .setBoundaryLabel('Boundary*', 4) // Define annotation boundary label
        .multichannelOutput(false) // If true, each label refers to the channel of a multichannel binary image (required for multiclass probability)
        .build()
    
    
    // Export each region
    int i = 0
    for (annotation in zStackSlice) {
        def region = RegionRequest.createInstance(
            labelServer.getPath(), downsample, zStackSlice)
        i++
        def outputPath = buildFilePath(pathOutput, 'Region ' + i + '.png')
        writeImageRegion(labelServer, region, outputPath)
    }
}
*/
/*
public static void exportMeasurements (ArrayList<PathObject> zStackSlice) {
    def outputDir = buildFilePath(PROJECT_BASE_DIR, 'Best_Frames')
    mkdirs(outputDir)
    
    def server = getCurrentServer()
    if (zStackSlice.get(0).getDescription().equals("Best Vacuole")) {
        selectObjects(zStackSlice.get(0))
    }    
    def roi = getSelectedROI()
    def requestROI = RegionRequest.createInstance(server.getPath(), 1, roi)
    writeImageRegion(server, requestROI, outputDir)
}
*/
/*
public static saveBestMeasurements (ArrayList<PathObject> zStackSlice) {
    def imageData = getCurrentImageData()
    def server = imageData.getServer()
    
    // Define output path (relative to project)
    def outputDir = buildFilePath(PROJECT_BASE_DIR, 'Best_Frames')
    mkdirs(outputDir)
    def name = GeneralTools.getNameWithoutExtension(imageData.getServer().getMetadata().getName())
       
    double downsample = 1.0
    int x = 0
    int y = 0
    int width = server.getWidth()
    int height = server.getHeight()
    int t = 0
    
        def labelServer = new LabeledImageServer.Builder(imageData)
          .backgroundLabel(255, ColorTools.BLACK) // Specify background label (usually 0 or 255)
          .addLabel('Vacuole', 1) // Choose output labels (the order matters!)
          .addLabel('Lo Domains', 2)
          .addLabel('Ld Domains', 3)
          .multichannelOutput(false) // If true, each label refers to the channel of a multichannel binary image (required for multiclass probability)
          .build()
          
    if (zStackSlice.get(0).getDescription().equals("Best Vacuole")) {
        selectObjects(zStackSlice.get(0))
    }      
    def roi = getSelectedROI()
    def requestROI = RegionRequest.createInstance(server.getPath(), 1, roi)
    writeImageRegion(server, requestROI, '/path/to/export/region.tif')
    
    for (int z = 0; z < server.nZSlices() ; z++){
        if (
        // get current z
        def path = buildFilePath(outputDir, name + "_z-" + z + ".png")
        def request = RegionRequest.createInstance(labelServer.getPath(), downsample, x, y, width, height, z, t)
        
        // Write the image
        writeImageRegion(labelServer, request, path)
    }
    

}
*/    




