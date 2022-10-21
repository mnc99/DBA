/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package lab1;

import Environment.Environment;
import agents.LARVAFirstAgent;
import ai.Choice;
import ai.DecisionSet;
import geometry.Point3D;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import tools.emojis;
import world.Perceptor;

/**
 *
 * @author ana
 */
public class AT_ST_FULL extends LARVAFirstAgent{

    enum Status {
        START, 
        CHECKIN, 
        OPENPROBLEM, 
        SOLVEPROBLEM, 
        CLOSEPROBLEM, 
        CHECKOUT, 
        EXIT,
        JOINSESSION
    }
    Status myStatus;   
    String service = "PMANAGER", 
            problem = "SandboxTesting", 
            problemManager = "", 
            sessionManager, 
            action="", preplan="", 
            content, 
            sessionKey; 
    ACLMessage open, session; 
    int iplan = 0;
    String[] contentTokens, 
            plan = new String[]{"MOVE","MOVE","MOVE","MOVE","MOVE","MOVE","MOVE","RIGHT","MOVE","MOVE","MOVE","MOVE","MOVE","RIGHT","MOVE","MOVE","MOVE","MOVE","MOVE","MOVE","MOVE","RIGHT","RIGHT","MOVE","MOVE","MOVE","MOVE","MOVE","MOVE","MOVE","LEFT","MOVE","RIGHT","RIGHT","MOVE","LEFT","MOVE","MOVE","MOVE","EXIT"}; // To parse the content

        
    protected String whichWall, nextWhichwall;
    protected double distance, nextdistance;
    protected Point3D point, nextPoint;

    
    @Override
    public void setup() {
        
        this.enableDeepLARVAMonitoring();
        super.setup();

       
        this.activateSequenceDiagrams();

        logger.onEcho();

        logger.onTabular();
                
        myStatus = Status.START;
        this.setupEnvironment();
        this.enableDeepLARVAMonitoring();
        A = new DecisionSet();
        A.addChoice(new Choice("MOVE")).
                addChoice(new Choice("LEFT")).
                addChoice(new Choice("RIGHT"));
        problem = "Halfmoon3";
    }

    @Override
    public void Execute() {
        Info("Status: " + myStatus.name());
        switch (myStatus) {
            case START:
                myStatus = Status.CHECKIN;
                break;
            case CHECKIN:
                myStatus = MyCheckin();
                break;
            case OPENPROBLEM:
                myStatus = MyOpenProblem();
                break;
            case JOINSESSION:
                myStatus = MyJoinSession();
                break;
            case SOLVEPROBLEM:
                myStatus = MySolveProblem();
                break;
            case CLOSEPROBLEM:
                myStatus = MyCloseProblem();
                break;
            case CHECKOUT:
                myStatus = MyCheckout();
                break;
            case EXIT:
            default:
                doExit();
                break;
        }
    }

    @Override
    public void takeDown() {
        Info("Taking down...");
        this.saveSequenceDiagram("./" + getLocalName() + ".seqd");
        super.takeDown();
    }

    public Status MyCheckin() {
        Info("Loading passport and checking-in to LARVA");

        if (!doLARVACheckin()) {
            Error("Unable to checkin");
            return Status.EXIT;
        }
        return Status.OPENPROBLEM;
    }

    public Status MyCheckout() {
        this.doLARVACheckout();
        return Status.EXIT;
    }

    public Status MyOpenProblem() {
        if (this.DFGetAllProvidersOf(service).isEmpty()) {
            Error("Service PMANAGER is down");
            return Status.CHECKOUT;
        }
        problemManager = this.DFGetAllProvidersOf(service).get(0);
        Info("Found problem manager " + problemManager);

        this.outbox = new ACLMessage();
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(problemManager, AID.ISLOCALNAME));
        outbox.setContent("Request open " + problem);
        this.LARVAsend(outbox);
        Info("Request opening problem " + problem + " to " + problemManager);
        
        open = LARVAblockingReceive();
        Info(problemManager + " says: " + open.getContent());
        content = open.getContent();
        contentTokens = content.split(" ");
        if (contentTokens[0].toUpperCase().equals("AGREE")) {
            sessionKey = contentTokens[4];
            session = LARVAblockingReceive();
            sessionManager = session.getSender().getLocalName();
            Info(sessionManager + " says: " + session.getContent());
            return Status.JOINSESSION;
        } else {
            Error(content);
            return Status.CHECKOUT;
        }
    }

    public Status MyJoinSession() {
        this.resetAutoNAV();
        this.DFAddMyServices(new String[]{"TYPE AT_ST"});
        outbox = session.createReply();
        outbox.setContent("Request join session "+sessionKey);
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        if(!session.getContent().startsWith("Confirm")){
            Error("Could not join session " + sessionKey+" due " + session.getContent());
            return Status.CLOSEPROBLEM;
        }
        this.openRemote();
        this.MyReadPerceptions();
        return Status.SOLVEPROBLEM;
    }
    
    @Override
    protected Choice Ag(Environment E, DecisionSet A){
        if(G(E)){
            return null;
        } else if(A.isEmpty()){
            return null;
        } else {
            A = Prioritize(E,A);
            whichWall = nextWhichwall;
            point = nextPoint;
            return A.BestChoice();
        }
    }
    
    public Status MySolveProblem() {
        if(G(E)){
            Message("Problem " + problem + " has been solved!");
            return Status.CLOSEPROBLEM;
        }
        if(!Ve(E)){
            Alert("Sorry but the agent has crashed!");
            return Status.CLOSEPROBLEM;
        }
        Choice a = this.Ag(E, A);
        if (a == null){
            Alert("Sorry, no action found!");
            return Status.CLOSEPROBLEM;
        }
        Info("Try to execute" +a);
        this.MyExecuteAction(a.getName());
        this.MyReadPerceptions();
        return Status.SOLVEPROBLEM;
        
    }
    
     public boolean MyReadPerceptions() {
        Info("Reading perceptions...");
        outbox = session.createReply();
        outbox.setContent("Query sensors session " + sessionKey);
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        if (session.getContent().startsWith("Failure")) {
            Error("Unable to read perceptions due to " + session.getContent());
            return false;
        }
        this.getEnvironment().setExternalPerceptions(session.getContent());
        Info(this.easyPrintPerceptions());
        return true;
    }
     
    public String PrintPerceptions() {
        String res;
        int matrix[][];

        if (getEnvironment() == null) {
            Error("Environment is unacessible, please setupEnvironment() first");
            return "";
        }

        res = "\n\nReading of sensors\n";
        if (getEnvironment().getName() == null) {
            res += emojis.WARNING + " UNKNOWN AGENT";
            return res;
        } else {
            res += emojis.ROBOT + " " + getEnvironment().getName();
        }
        res += "\n";
        res += String.format("%10s: %05d W\n", "ENERGY",getEnvironment().getEnergy());
        res += String.format("%10s: %15s\n", "POSITION", getEnvironment().getGPS().toString());
        res += String.format("%10s: %05d m\n", "X", getEnvironment().getGPS().getXInt())
                + String.format("%10s: %05d m\n", "Y", getEnvironment().getGPS().getYInt())
                + String.format("%10s: %05d m\n", "Z", getEnvironment().getGPS().getZInt())
                + String.format("%10s: %05d m\n", "RANGE", getEnvironment().getRange())
                + String.format("%10s: %05d m\n", "MAXLEVEL", getEnvironment().getMaxlevel())
                + String.format("%10s: %05d m\n", "MAXSLOPE", getEnvironment().getMaxslope());
        res += String.format("%10s: %05d m\n", "GROUND", getEnvironment().getGround());
        res += String.format("%10s: %05d º\n", "COMPASS", getEnvironment().getCompass());
        if (getEnvironment().getTarget() == null) {
            res += String.format("%10s: " + "!", "TARGET");
        } else {
            res += String.format("%10s: %05.2f m\n", "DISTANCE", getEnvironment().getDistance());
            res += String.format("%10s: %05.2f º\n", "ABS ALPHA", getEnvironment().getAngular());
            res += String.format("%10s: %05.2f º\n", "REL ALPHA", getEnvironment().getRelativeAngular());
        }
        res += "\nVISUAL ABSOLUTE\n";
        matrix = getEnvironment().getAbsoluteVisual();
        for (int y = 0; y < matrix[0].length; y++) {
            for (int x = 0; x < matrix.length; x++) {
                res += printValue(matrix[x][y]);
            }
            res += "\n";
        }
        for (int x = 0; x < matrix.length; x++) {
            if (x != matrix.length / 2) {
                res += "----";
            } else {
                res += "[  ]-";
            }
        }
        res += "\nVISUAL RELATIVE\n";
        matrix = getEnvironment().getRelativeVisual();
        for (int y = 0; y < matrix[0].length; y++) {
            for (int x = 0; x < matrix.length; x++) {
                res += printValue(matrix[x][y]);
            }
            res += "\n";
        }
        for (int x = 0; x < matrix.length; x++) {
            if (x != matrix.length / 2) {
                res += "----";
            } else {
                res += "[  ]-";
            }
        }
        res += "VISUAL POLAR\n";
        matrix = getEnvironment().getPolarVisual();
        for (int y = 0; y < matrix[0].length; y++) {
            for (int x = 0; x < matrix.length; x++) {
                res += printValue(matrix[x][y]);
            }
            res += "\n";
        }
        res += "\n";
        res += "LIDAR RELATIVE\n";
        matrix = getEnvironment().getRelativeLidar();
        for (int y = 0; y < matrix[0].length; y++) {
            for (int x = 0; x < matrix.length; x++) {
                res += printValue(matrix[x][y]);
            }
            res += "\n";
        }
        for (int x = 0; x < matrix.length; x++) {
            if (x != matrix.length / 2) {
                res += "----";
            } else {
                res += "-^^-";
            }
        }
        res += "\n";
        return res;
    }
    
    protected String printValue(int v) {
        if (v == Perceptor.NULLREAD) {
            return "XXX ";
        } else {
            return String.format("%03d ", v);
        }
    }

    protected String printValue(double v) {
        if (v == Perceptor.NULLREAD) {
            return "XXX ";
        } else {
            return String.format("%05.2f ", v);
        }
    }
    
    
    public boolean MyExecuteAction(String action) {
        Info("Executing action " + action);
        outbox = session.createReply();
        outbox.setContent("Request execute " + action + " session " + sessionKey);
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        if (!session.getContent().startsWith("Inform")) {
            Error("Unable to execute action " + action + " due to " + session.getContent());
            return false;
        }
        return true;
    }

    public Status MyCloseProblem() {
        Info("Plan = "+preplan );
        outbox = open.createReply();
        outbox.setContent("Cancel session " + sessionKey);
        Info("Closing problem Helloworld, session " + sessionKey);
        this.LARVAsend(outbox);
        inbox = LARVAblockingReceive();
        Info(problemManager + " says: " + inbox.getContent());
        return Status.CHECKOUT;
    }
    
    protected double goAhead(Environment E, Choice a){
        if(a.getName().equals("MOVE")){
            return U(S(E,a));  
        } else { 
            return U(S(E,a), new Choice("MOVE"));  
        }
    }
    
        public double goAvoid(Environment E, Choice a) {
        if (a.getName().equals("RIGHT")) {
            nextWhichwall = "LEFT";
            nextdistance = E.getDistance();
            nextPoint = E.getGPS();
            return Choice.ANY_VALUE;
        }
        return Choice.MAX_UTILITY;
    }
    
    @Override
    protected double U(Environment E, Choice a) {
        if (whichWall.equals("LEFT")) {
            return goFollowWallLeft(E, a);
        } else if (!E.isFreeFront()) {
            return goAvoid(E, a);
        } else {
            return goAhead(E, a);
        }
    }

    public String easyPrintPerceptions(){
        return PrintPerceptions()+"\n"+
                this.Prioritize(E, A).toString()+ "\nWall:\n" + whichWall + "\n";
    }
    
     public double goFollowWallLeft(Environment E, Choice a) {
         Info("Siguiendo pared");
         if (E.isFreeFrontLeft()) {
            return goTurnOnWallLeft(E, a);
        } else if (E.isTargetFrontRight()
                && E.isFreeFrontRight()
                && E.getDistance() < point.planeDistanceTo(E.getTarget())) {
            return goStopWallLeft(E, a);
        } else if (E.isFreeFront()) {
            return goKeepOnWall(E, a);
        } else {
            return goRevolveWallLeft(E, a);
        }

    }

    public double goKeepOnWall(Environment E, Choice a) {
        if (a.getName().equals("MOVE")) {
            return Choice.ANY_VALUE;
        }
        return Choice.MAX_UTILITY;
    }

    public double goTurnOnWallLeft(Environment E, Choice a) {
        if (a.getName().equals("LEFT")) {
            return Choice.ANY_VALUE;
        }
        return Choice.MAX_UTILITY;

    }

    public double goRevolveWallLeft(Environment E, Choice a) {
        if (a.getName().equals("RIGHT")) {
            return Choice.ANY_VALUE;
        }
        return Choice.MAX_UTILITY;
    }

    public double goStopWallLeft(Environment E, Choice a) {
        if (a.getName().equals("RIGHT")) {
            this.resetAutoNAV();
            return Choice.ANY_VALUE;
        }
        return Choice.MAX_UTILITY;
    }

    public void resetAutoNAV() {
        nextWhichwall = whichWall = "NONE";
        nextdistance = distance = Choice.MAX_UTILITY;
        nextPoint = point = null;
    }
    
}
