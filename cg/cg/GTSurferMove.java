package cg;

import org.encog.engine.network.activation.ActivationSigmoid;
import org.encog.ml.data.MLDataPair;
import org.encog.ml.data.MLDataSet;
import org.encog.ml.data.basic.BasicMLData;
import org.encog.ml.data.basic.BasicMLDataPair;
import org.encog.ml.data.basic.BasicMLDataSet;
import org.encog.neural.networks.BasicNetwork;
import org.encog.neural.networks.layers.BasicLayer;
import org.encog.neural.networks.training.propagation.back.Backpropagation;
import robocode.*;
import robocode.util.Utils;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by tanczosm on 10/14/2015.
 */
public class GTSurferMove extends BaseMove {

    private AdvancedRobot _robot;
    private RadarScanner _radarScanner;

    private boolean showDebugGraphics = true;

    // Neural network stuff
    public BasicNetwork basicNetwork;
    public Backpropagation basicTrain;
    private ArrayList<MLDataPair> _theData;
    private ArrayList<MLDataPair> _lastHitsData;

    public static final int INPUT_LENGTH = 44;
    public static final int OUTPUT_LENGTH = 61;

    public static int BINS = OUTPUT_LENGTH;
    public static double _surfStats[] = new double[BINS]; // we'll use 47 bins
    public Point2D.Double _myLocation;     // our bot's location
    public Point2D.Double _enemyLocation;  // enemy bot's location

    public Point2D.Double _lastGoToPoint;
    public double direction = 1;

    public ArrayList _enemyWaves;
    public ArrayList _surfData;
    public ArrayList<Double> _LateralVelocityLast10;

    double _lastVelocity = 0;
    int _lastDirection = 1;
    int _lastDirectionTimeChange = 0;

    private class surfData
    {
        public Point2D.Double enemyLocation;
        public Point2D.Double playerLocation;

        public int direction;
        public double lateralVelocity;
        public double lateralDistanceLast10;
        public double absBearing;
        public double acceleration;
        public int timeSinceDirectionChange;
        public double forwardWallDistance;
        public double reverseWallDistance;
    }

    public GTSurferMove (AdvancedRobot robot, RadarScanner radarScanner)
    {
        _robot = robot;
        _radarScanner = radarScanner;

        _enemyWaves = new ArrayList();
        _surfData = new ArrayList();
        _LateralVelocityLast10 = new ArrayList<Double>();
        _enemyLocation = new Point2D.Double();

        basicNetwork = new BasicNetwork();
        basicNetwork.addLayer(new BasicLayer(null, true, INPUT_LENGTH));
        //basicNetwork.addLayer(new BasicLayer(new ActivationSigmoid(), true, 39));
        basicNetwork.addLayer(new BasicLayer(new ActivationSigmoid(), false, OUTPUT_LENGTH));
        basicNetwork.getStructure().finalizeStructure();
        basicNetwork.reset();
        basicNetwork.reset(1000);

        _theData = new ArrayList<MLDataPair>();
        _lastHitsData = new ArrayList<MLDataPair>();
        _theData.add(new BasicMLDataPair(new BasicMLData(new double[INPUT_LENGTH]), new BasicMLData(new double[OUTPUT_LENGTH])));
        MLDataSet trainingSet = new BasicMLDataSet(_theData);
        basicTrain = new Backpropagation(basicNetwork, trainingSet, 0.7, 0.3);
        basicTrain.setBatchSize(1);

    }

    public String getName ()
    {
        return "GTSurfer";
    }

    /* Call mover events */
    public void onBulletHit(BulletHitEvent e) {
    }


    public void onBulletMissed(BulletMissedEvent e)
    {
    }



    // This is a rectangle that represents an 800x600 battle field,
// used for a simple, iterative WallSmoothing method (by Kawigi).
// If you're not familiar with WallSmoothing, the wall stick indicates
// the amount of space we try to always have on either end of the tank
// (extending straight out the front or back) before touching a wall.
    public static Rectangle2D.Double _fieldRect
            = new java.awt.geom.Rectangle2D.Double(18, 18, 764, 564);
    public static double WALL_STICK = 160;

    public void scan(ScannedRobotEvent e) {
        _myLocation = new Point2D.Double(_robot.getX(), _robot.getY());

        double lateralVelocity = _robot.getVelocity()*Math.sin(e.getBearingRadians());
        double absBearing = e.getBearingRadians() + _robot.getHeadingRadians();
        double velocity = _robot.getVelocity();
        int direction = new Integer((lateralVelocity >= 0) ? 1 : -1);
        int timeSinceDirectionChange = 0;

        _robot.setTurnRadarRightRadians(Utils.normalRelativeAngle(absBearing - _robot.getRadarHeadingRadians()) * 2);

        double acceleration = 0;
        if (_lastVelocity != Double.MAX_VALUE) {

            if (CTUtils.sign(_lastVelocity) == CTUtils.sign(velocity)) {
                acceleration = Math.abs(velocity) - Math.abs(_lastVelocity);
            } else {
                acceleration = Math.abs(velocity - _lastVelocity);
            }
        } else {
            acceleration = velocity;
        }
        acceleration = Math.abs(Math.max(Math.min(acceleration, 2d), -2d));

        _LateralVelocityLast10.add(Math.abs(lateralVelocity));
        if (_LateralVelocityLast10.size() > 10)
            _LateralVelocityLast10.remove(0);

        double LatVelLast10 = 0;
        for (int k = 0; k < _LateralVelocityLast10.size(); k++) {
            LatVelLast10 += _LateralVelocityLast10.get(k);
        }

        if (direction != _lastDirection)
        {
            _lastDirectionTimeChange = (int)_robot.getTime();
        }

        surfData sd = new surfData();
        sd.enemyLocation = (Point2D.Double)_enemyLocation.clone();
        sd.playerLocation = new Point2D.Double(_robot.getX(), _robot.getY());
        sd.lateralVelocity = lateralVelocity;
        sd.direction =  direction;
        sd.absBearing = new Double(absBearing + Math.PI);
        sd.acceleration = acceleration;
        sd.lateralDistanceLast10 = LatVelLast10;
        sd.timeSinceDirectionChange = (int)_robot.getTime() - _lastDirectionTimeChange;
        sd.forwardWallDistance = CTUtils.wallDistance(sd.enemyLocation.x, sd.enemyLocation.y, e.getDistance(),  absBearing + Math.PI, direction );
        sd.reverseWallDistance = CTUtils.wallDistance(sd.enemyLocation.x, sd.enemyLocation.y, e.getDistance(), absBearing + Math.PI, -direction );
        _surfData.add(0, sd);

        //System.out.println("fd: " + sd.forwardWallDistance + ", rd: " + sd.reverseWallDistance);

// Need location, lateral velocity, heading, lateral direction, advancing velocity and angle from enemy

        double bulletPower = _radarScanner._oppEnergy - e.getEnergy();
        if (bulletPower < 3.01 && bulletPower > 0.09
                && _surfData.size() > 2) {

            surfData _surf = ((surfData)_surfData.get(2));

            EnemyWave ew = new EnemyWave();
            ew.fireTime = _robot.getTime() - 1;
            ew.bulletVelocity = CTUtils.bulletVelocity(bulletPower);
            ew.distanceTraveled = CTUtils.bulletVelocity(bulletPower);
            ew.direction = _surf.direction;
            ew.directAngle = _surf.absBearing;
            ew.fireLocation = (Point2D.Double)_enemyLocation.clone(); // last tick

            ew.acceleration = _surf.acceleration;
            ew.lateralDistanceLast10 = _surf.lateralDistanceLast10;
            ew.timeSinceDirectionChange = _surf.timeSinceDirectionChange;
            ew.forwardWallDistance = _surf.forwardWallDistance;
            ew.reverseWallDistance = _surf.reverseWallDistance;
            ew.lateralVelocity = _surf.lateralVelocity;

            _enemyWaves.add(ew);
        }

        _lastVelocity = velocity;
        _lastDirection = direction;

        _radarScanner._oppEnergy = e.getEnergy();

        // update after EnemyWave detection, because that needs the previous
        // enemy location as the source of the wave
        _enemyLocation = CTUtils.project(_myLocation, absBearing, e.getDistance());

    }

    public void update(ScannedRobotEvent e) {

        updateWaves();
        doSurfing();

        drawFactor(_surfStats, 0, _surfStats.length, "Firing Output", 5, 5, 0);
    }

    public void drawFactor(double[] data, int factorStart, int featureCount, String featureName, int topx, int topy, int position) {
        Graphics2D g = _robot.getGraphics();

        double graphWidth = 150;
        int height = 45;

        topy = (height + 5) * position;
        int graphx = topx, graphy = topy + height - 10, cnt = 0;
        int rightBorder = graphx + ((int) graphWidth - (int) (graphWidth / featureCount));
        g.setColor(Color.GREEN);
        g.drawLine(graphx, graphy, rightBorder, graphy);
        g.drawLine(graphx, graphy, graphx, graphy + (height - 10));
        g.drawLine(rightBorder, graphy, rightBorder, graphy + (height - 10));

        g.setColor(Color.white);
        Point2D.Double lastpoint = new Point2D.Double(graphx, graphy);

        g.setFont(new Font("Verdana", Font.PLAIN, 10));
        g.drawString(featureName, graphx, graphy - 10);

        for (int i = factorStart; i < factorStart + featureCount && i < data.length; i++) {
            g.setColor(Color.MAGENTA);
            //g.drawOval(graphx + (int) (cnt * (graphWidth / featureCount)) - 1, graphy + (int) (data[i] * 20), 2, 2);
            Point2D.Double nextpoint = new Point2D.Double(graphx + (int) (cnt * (graphWidth / featureCount)) - 1, graphy + (int) (data[i] * (height - 10)));
            g.drawLine((int) lastpoint.x, (int) lastpoint.y, (int) nextpoint.x, (int) nextpoint.y);

            lastpoint = nextpoint;

            cnt++;
        }

        //Point2D.Double nextpoint = new Point2D.Double(graphx + (int) graphWidth, graphy);
        //g.drawLine((int)lastpoint.x, (int)lastpoint.y, (int)nextpoint.x, (int)nextpoint.y);

    }

    public double[] getInputForWave(EnemyWave w) {

        // Distance - Range 0 - 800, split into 11 features
        double bft = w.playerDistance / _radarScanner.FIRE_SPEED;
        double[] fdistance = RBFUtils.processDataIntoFeatures(Math.min(bft, 105), 105, RBFUtils.getCenters(0, 105, 11));

        // Acceleration - Range 0 - 1.0, split into 7 features
        double[] faccel = RBFUtils.processDataIntoFeatures(w.acceleration, 0.1, RBFUtils.getCenters(-2, 2, 7));

        // Lateral Velocity - Range -8 - 8, split into 8 features
        double[] flatvel = RBFUtils.processDataIntoFeatures(w.lateralVelocity, 2.0, RBFUtils.getCenters(-8, 8, 8));

        // SinceDirectionChange - Range 0 - bft, split into 7 features
        double[] fsincedirch = RBFUtils.processDataIntoFeatures(Math.min(w.timeSinceDirectionChange, bft), 1, RBFUtils.getCenters(0, bft, 7));

        double wrdf = Math.min(1.5, w.forwardWallDistance);
        double wrdb = Math.min(1.0, w.reverseWallDistance);

        // Forward radians to wall - Range 0.0 - 1.5, split into 7 features
        double[] ffwrdf = RBFUtils.processDataIntoFeatures(wrdf, 0.05, RBFUtils.getCenters(0, 1.5, 7));

        // Back radians to wall - Range 0.0 - 1.0, split into 4 features
        double[] ffwrdb = RBFUtils.processDataIntoFeatures(wrdb, 0.05, RBFUtils.getCenters(0, 1.0, 4));

        return RBFUtils.mergeFeatures(fdistance, faccel, flatvel, fsincedirch, ffwrdf, ffwrdb);

    }

    public void updateWaves() {
        for (int x = 0; x < _enemyWaves.size(); x++) {
            EnemyWave ew = (EnemyWave)_enemyWaves.get(x);

            ew.distanceTraveled = (_robot.getTime() - ew.fireTime) * ew.bulletVelocity;
            if (ew.distanceTraveled >
                    _myLocation.distance(ew.fireLocation) + 50) {
                logHit(ew, _myLocation, false);
                _enemyWaves.remove(x);
                x--;
            }
        }
    }

    public BestWaves getClosestSurfableWave() {
        double closestDistance = 50000; // I juse use some very big number here
        EnemyWave surfWave = null;
        EnemyWave surfWave2 = null;

        for (int x = 0; x < _enemyWaves.size(); x++) {
            EnemyWave ew = (EnemyWave)_enemyWaves.get(x);
            double distance = _myLocation.distance(ew.fireLocation)
                    - ew.distanceTraveled;

            if (distance > ew.bulletVelocity && distance < closestDistance) {
                surfWave2 = surfWave;
                surfWave = ew;
                closestDistance = distance;
            }
        }

        if (surfWave != null) {
            BasicMLData inp = new BasicMLData(getInputForWave(surfWave));
            surfWave.waveGuessFactors = basicNetwork.compute(inp).getData();
            _surfStats = surfWave.waveGuessFactors;
        }

        if (surfWave2 != null) {
            BasicMLData inp = new BasicMLData(getInputForWave(surfWave2));
            surfWave2.waveGuessFactors = basicNetwork.compute(inp).getData();
        }
        //System.out.println("Guessfactor Output: " + Arrays.toString(_surfStats));

        return new BestWaves(surfWave, surfWave2);
    }

    public static double getGuessFactor(EnemyWave ew, Point2D.Double targetLocation) {
        double offsetAngle = (CTUtils.absoluteBearing(ew.fireLocation, targetLocation)
                - ew.directAngle);
        double factor = Utils.normalRelativeAngle(offsetAngle)
                / CTUtils.maxEscapeAngle(ew.bulletVelocity) * ew.direction;

        return factor;
    }

    // Given the EnemyWave that the bullet was on, and the point where we
// were hit, calculate the index into our stat array for that factor.
    public static int getFactorIndex(EnemyWave ew, Point2D.Double targetLocation) {

        double factor = getGuessFactor(ew, targetLocation);

        return getFactorIndex(factor);
    }

    public static int getFactorIndex(double factor)
    {
        return (int)CTUtils.limit(0,
                (factor * ((BINS - 1) / 2)) + ((BINS - 1) / 2),
                BINS - 1);
    }

    // Given the EnemyWave that the bullet was on, and the point where we
// were hit, update our stat array to reflect the danger in that area.
    public void logHit(EnemyWave ew, Point2D.Double targetLocation, boolean isHit) {

        if (!isHit)
            return;  // Bail.. don't log hits that aren't real

        /*
        int index = getFactorIndex(ew, targetLocation);

        for (int x = 0; x < BINS; x++) {
            // for the spot bin that we were hit on, add 1;
            // for the bins next to it, add 1 / 2;
            // the next one, add 1 / 5; and so on...
            _surfStats[x] += 1.0 / (Math.pow(index - x, 2) + 1);
        }
        */
        double enemyX = ew.fireLocation.getX(), enemyY = ew.fireLocation.getY();
        double startX = targetLocation.getX(), startY = targetLocation.getY();

        double gf = getGuessFactor(ew, targetLocation);
        double gfwidth = CTUtils.botWidthAimAngle(Math.sqrt((enemyX-startX)*(enemyX-startX) + (enemyY-startY)*(enemyY-startY) ));

        double[] centers = RBFUtils.getCenters(-1.0, 1.0, 61);
        double[] ideal = RBFUtils.processDataIntoFeatures(gf, gfwidth*4, centers);

        _theData.clear();
        _theData.add(new BasicMLDataPair(new BasicMLData(getInputForWave(ew)), new BasicMLData(ideal)));

        for (int i = 0; i < _lastHitsData.size(); i++)
        {
            _theData.add(_lastHitsData.get(i));
        }

        basicTrain.iteration(2);

        if (isHit && 2==3)
        {
            _lastHitsData.add(_theData.get(_theData.size()-1));

            if (_lastHitsData.size() > 5)
                _lastHitsData.remove(0);
        }

    }

    public void onBulletHitBullet(BulletHitBulletEvent e)
    {

        if (!_enemyWaves.isEmpty()) {
            Point2D.Double hitBulletLocation = new Point2D.Double(
                    e.getBullet().getX(), e.getBullet().getY());
            EnemyWave hitWave = null;

            // look through the EnemyWaves, and find one that could've hit us.
            for (int x = 0; x < _enemyWaves.size(); x++) {
                EnemyWave ew = (EnemyWave)_enemyWaves.get(x);

                if (Math.abs(ew.distanceToPoint(hitBulletLocation) - ew.distanceTraveled) < 50) {
                    hitWave = ew;
                    break;
                }
            }

            if (hitWave != null) {
                logHit(hitWave, hitBulletLocation, true);

                // We can remove this wave now, of course.
                _enemyWaves.remove(_enemyWaves.lastIndexOf(hitWave));
            }
        }

    }

    public void onHitByBullet(HitByBulletEvent e) {

        // If the _enemyWaves collection is empty, we must have missed the
        // detection of this wave somehow.
        if (!_enemyWaves.isEmpty()) {
            Point2D.Double hitBulletLocation = new Point2D.Double(
                    e.getBullet().getX(), e.getBullet().getY());
            EnemyWave hitWave = null;

            // look through the EnemyWaves, and find one that could've hit us.
            for (int x = 0; x < _enemyWaves.size(); x++) {
                EnemyWave ew = (EnemyWave)_enemyWaves.get(x);

                if (Math.abs(ew.distanceTraveled -
                        _myLocation.distance(ew.fireLocation)) < 50
                        && Math.abs(CTUtils.bulletVelocity(e.getBullet().getPower())
                        - ew.bulletVelocity) < 0.001) {
                    hitWave = ew;
                    break;
                }
            }

            if (hitWave != null) {
                logHit(hitWave, hitBulletLocation, true);

                // We can remove this wave now, of course.
                _enemyWaves.remove(_enemyWaves.lastIndexOf(hitWave));
            }
        }
    }

    // CREDIT: mini sized predictor from Apollon, by rozu
// http://robowiki.net?Apollon
    public ArrayList predictPositions(EnemyWave surfWave, int direction) {
        Point2D.Double predictedPosition = (Point2D.Double)_myLocation.clone();
        double predictedVelocity = _robot.getVelocity();
        double predictedHeading = _robot.getHeadingRadians();
        double maxTurning, moveAngle, moveDir;
        ArrayList traveledPoints = new ArrayList();

        int counter = 0; // number of ticks in the future
        boolean intercepted = false;

        do {
            double distance = predictedPosition.distance(surfWave.fireLocation);
            double offset = Math.PI/2 - 1 + distance/400;

            moveAngle =
                    CTUtils.wallSmoothing(_fieldRect, _robot.getBattleFieldWidth(), _robot.getBattleFieldHeight(),
                                        predictedPosition,  CTUtils.absoluteBearing(surfWave.fireLocation,
                                    predictedPosition) + (direction * (offset)), direction, WALL_STICK)
                                    - predictedHeading;
            /*
                    GTSurferMove.wallSmoothing(predictedPosition, CTUtils.absoluteBearing(surfWave.fireLocation,
                            predictedPosition) + (direction * (offset)), direction)
                            - predictedHeading;*/
            moveDir = 1;

            if(Math.cos(moveAngle) < 0) {
                moveAngle += Math.PI;
                moveDir = -1;
            }

            moveAngle = Utils.normalRelativeAngle(moveAngle);

            // maxTurning is built in like this, you can't turn more then this in one tick
            maxTurning = Math.PI/720d*(40d - 3d*Math.abs(predictedVelocity));
            predictedHeading = Utils.normalRelativeAngle(predictedHeading
                    + CTUtils.limit(-maxTurning, moveAngle, maxTurning));

            // this one is nice ;). if predictedVelocity and moveDir have
            // different signs you want to breack down
            // otherwise you want to accelerate (look at the factor "2")
            predictedVelocity += (predictedVelocity * moveDir < 0 ? 2*moveDir : moveDir);
            predictedVelocity = CTUtils.limit(-8, predictedVelocity, 8);

            // calculate the new predicted position
            predictedPosition = CTUtils.project(predictedPosition, predictedHeading, predictedVelocity);

            //add this point the our prediction
            traveledPoints.add(predictedPosition);

            counter++;

            if (predictedPosition.distance(surfWave.fireLocation) - 20 <
                    surfWave.distanceTraveled + (counter * surfWave.bulletVelocity)
                //   + surfWave.bulletVelocity
                    ) {
                intercepted = true;
            }
        } while(!intercepted && counter < 500);

        //we can't get the the last point, because we need to slow down
        if(traveledPoints.size() > 1)
            traveledPoints.remove(traveledPoints.size() - 1);

        return traveledPoints;
    }

    public double checkDanger(EnemyWave surfWave, Point2D.Double position) {
        int index = getFactorIndex(surfWave, position);
        double distance = position.distance(surfWave.fireLocation);
        return surfWave.waveGuessFactors[index]/distance;
    }

    // Calculates the average danger over a particular span of guess factors
    public double checkDangerSpan(EnemyWave surfWave, Point2D.Double position, int totalSpan)
    {
        int index = getFactorIndex(surfWave, position);
        int halfSpan = (int)Math.max(1.0, Math.ceil(totalSpan/2.0));
        int startIndex = (int)CTUtils.clamp(index-halfSpan, 0, index);
        int endIndex = (int)CTUtils.clamp(index+halfSpan, index, OUTPUT_LENGTH-1);

        double danger = 0.0;
        for (int i = startIndex; i <= endIndex; i++)
        {
            danger += surfWave.waveGuessFactors[i];
        }
        //danger /= totalSpan;

        return danger;
    }

    public Point2D.Double getBestPoint(EnemyWave surfWave){

        double distance = surfWave.distanceTraveled;

        // Calculate how much of the wave we need to be concerned with
        double botWidthAimAngle = CTUtils.botWidthAimAngle(distance);

        double gfSpan = 2.0 / (double)OUTPUT_LENGTH;
        int totalSpan = (int)Math.max(Math.ceil(botWidthAimAngle / gfSpan), 1.0);
        //System.out.println("Span: " + totalSpan + " gfSpan: " + gfSpan + ", botWidthAimAngle: " + botWidthAimAngle);

        if(surfWave.safePoints == null){
            ArrayList forwardPoints = predictPositions(surfWave, 1);
            ArrayList reversePoints = predictPositions(surfWave, -1);
            int FminDangerIndex = 0;
            int RminDangerIndex = 0;
            double FminDanger = Double.POSITIVE_INFINITY;
            double RminDanger = Double.POSITIVE_INFINITY;
            for(int i = 0, k = forwardPoints.size(); i < k; i++){
                double thisDanger = checkDangerSpan(surfWave, (Point2D.Double) (forwardPoints.get(i)), totalSpan);
                //double thisDanger = checkDanger(surfWave, (Point2D.Double) (forwardPoints.get(i)));

                if(thisDanger <= FminDanger){
                    FminDangerIndex = i;
                    FminDanger = thisDanger;
                }
            }
            for(int i = 0, k = reversePoints.size(); i < k; i++){
                double thisDanger = checkDangerSpan(surfWave, (Point2D.Double)(reversePoints.get(i)), totalSpan);
                //double thisDanger = checkDanger(surfWave, (Point2D.Double) (reversePoints.get(i)));

                if(thisDanger <= RminDanger){
                    RminDangerIndex = i;
                    RminDanger = thisDanger;
                }
            }
            ArrayList bestPoints;
            int minDangerIndex;

            if(FminDanger < RminDanger ){
                bestPoints = forwardPoints;
                minDangerIndex = FminDangerIndex;
            }
            else {
                bestPoints = reversePoints;
                minDangerIndex = RminDangerIndex;
            }

            Point2D.Double bestPoint = (Point2D.Double)bestPoints.get(minDangerIndex);

            while(bestPoints.indexOf(bestPoint) != -1)
                bestPoints.remove(bestPoints.size() - 1);
            bestPoints.add(bestPoint);

            surfWave.safePoints = bestPoints;

            //debugging - so that we should always be on top of the last point
            bestPoints.add(0,new Point2D.Double(_robot.getX(), _robot.getY()));

        }
        else
        if(surfWave.safePoints.size() > 1)
            surfWave.safePoints.remove(0);


        if(surfWave.safePoints.size() >= 1){
            for(int i = 0,k=surfWave.safePoints.size(); i < k; i++){
                Point2D.Double goToPoint = (Point2D.Double)surfWave.safePoints.get(i);
                if(goToPoint.distanceSq(_myLocation) > 20*20*1.1)
                    //if it's not 20 units away we won't reach max velocity
                    return goToPoint;
            }
            //if we don't find a point 20 units away, return the end point
            return (Point2D.Double)surfWave.safePoints.get(surfWave.safePoints.size() - 1);


        }

        return null;
    }

    public void doSurfing() {
        BestWaves best = getClosestSurfableWave();
        EnemyWave surfWave = best.firstWave;
        double distance = _enemyLocation.distance(_myLocation);
        if (surfWave == null || distance < 50) {
            //do 'away' movement  best distance of 400 - modified from RaikoNano
            double absBearing = CTUtils.absoluteBearing(_myLocation, _enemyLocation);
            double headingRadians = _robot.getHeadingRadians();
            double stick = 160;//Math.min(160,distance);
            double  v2, offset = Math.PI/2 + 1 - distance/400;

            while(!_fieldRect.
                    contains(CTUtils.project(_myLocation, v2 = absBearing + direction * (offset -= 0.02), stick)

                            // 	getX() + stick * Math.sin(v2 = absBearing + direction * (offset -= .02)), getY() + stick * Math.cos(v2)
                    ));


            if( offset < Math.PI/3 )
                direction = -direction;
            _robot.setAhead(50 * Math.cos(v2 - headingRadians));
            _robot.setTurnRightRadians(Math.tan(v2 - headingRadians));

        }
        else {
            Point2D.Double p1 = getBestPoint(best.firstWave);
            /*
            if (best.secondWave != null) {
                Point2D.Double temp = _myLocation;
                _myLocation = p1;
                Point2D.Double p2 = getBestPoint(best.secondWave);
                _myLocation = temp;

                if (p2.distance(_myLocation) > p1.distance(_myLocation) && p2.distance(p1) < p2.distance(_myLocation)) {
                    goTo(p2);
                    System.out.println("Advancing to p2");
                }
                else
                    goTo(p1);
            }
            else
                goTo(p1);
            */
            goTo(p1);
        }
    }

    private void goTo(Point2D.Double destination) {
        if(destination == null){
            if(_lastGoToPoint != null)
                destination = _lastGoToPoint;
            else
                return;
        }

        _lastGoToPoint = destination;
        Point2D.Double location = new Point2D.Double(_robot.getX(), _robot.getY());
        double distance = location.distance(destination);
        double angle = Utils.normalRelativeAngle(CTUtils.absoluteBearing(location, destination) - _robot.getHeadingRadians());
        if (Math.abs(angle) > Math.PI/2) {
            distance = -distance;
            if (angle > 0) {
                angle -= Math.PI;
            }
            else {
                angle += Math.PI;
            }
        }

        //this is hacked so that the bot doesn't turn once we get to our destination
        _robot.setTurnRightRadians(angle * Math.signum(Math.abs((int) distance)));

        _robot.setAhead(distance);
    }

    public static class BestWaves
    {
        public EnemyWave firstWave; // by time
        public EnemyWave secondWave; // by distance

        public BestWaves (EnemyWave first, EnemyWave second)
        {
            firstWave = first;
            secondWave = second;
        }
    }

}
