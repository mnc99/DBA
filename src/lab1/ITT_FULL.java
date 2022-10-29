/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package starwars;

import Environment.Environment;
import agents.DEST;
import agents.LARVAFirstAgent;
import ai.Choice;
import ai.DecisionSet;
import ai.Plan;
import geometry.Point3D;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import java.util.ArrayList;
import java.util.StringTokenizer;
import tools.emojis;
import world.Perceptor;

/**
 *
 * @author ana
 */
public class ITT_FULL extends LARVAFirstAgent{

    private ACLMessage outboxRep;

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
            problem, 
            problemManager = "", 
            miDEST = "",
            sessionManager,  
            content, 
            sessionKey,
            report = "",
            ciudad_seleccionada = ""; 
    ACLMessage open, session, openRep;
    String[] contentTokens, ciudades;
    String[] problems = {"Dagobah.Apr1", "Dagobah.Apr2", "Dagobah.Not1",
            "Dagobah.Not2", "Endor.Sob1", "Endor.Sob2", "Endor.Hon1", "Endor.Hon2"};
    ArrayList<String> listaDEST;

    
        
    protected String whichWall, nextWhichwall;
    protected double distance, nextdistance;
    protected Point3D point, nextPoint;
    
    Plan behaviour = null;
    Environment Ei, Ef;
    Choice a;
    
    String goalActual;
    Boolean startedGoal = false;
    
    public Plan AgPlan(Environment E, DecisionSet A) {
        
        Ei = E.clone();
        Plan p = new Plan();
        
        for (int i = 0; i < Ei.getRange()/2 -2; i++) {
            Ei.cache();
            if (!Ve(Ei)) {
                return null;
            }
            else if (G(Ei)) {
                return p;
            }
            else {
                a = Ag(Ei, A);
                if (a != null) {
                    p.add(a);
                    Ef = S(Ei, a);
                    Ei = Ef;
                }
                else {
                    return null;
                }
            }
        }
        
        return p;
    }
    
    @Override
    public void setup() {
        
        this.enableDeepLARVAMonitoring();
        super.setup();

       
        this.activateSequenceDiagrams();
        //this.deactivateSequenceDiagrams();

        logger.onEcho();

        logger.onTabular();
                
        myStatus = Status.START;
        this.setupEnvironment();
        this.enableDeepLARVAMonitoring();
        A = new DecisionSet();
        A.addChoice(new Choice("MOVE")).
                addChoice(new Choice("LEFT")).
                addChoice(new Choice("RIGHT"));
        problem = this.inputSelect("Please, select the problem:", problems, " ");
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
        outbox.setContent("Request open " + problem + " alias " + sessionAlias);
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
        Info("Querying CITIES");
        outbox = session.createReply();
        outbox.setContent("Query CITIES session " + sessionKey);
        this.LARVAsend(outbox);
        session  = LARVAblockingReceive();
        this.getEnvironment().setExternalPerceptions(session.getContent());

        //ciudades = getEnvironment().getExternalPerceptions().split(" "); 
        ciudades = getEnvironment().getCityList();
        ciudad_seleccionada = this.inputSelect("Please select the city to start: ", ciudades, ciudades[0]);



        this.resetAutoNAV();
        this.DFAddMyServices(new String[]{"TYPE ITT"});
        outbox = session.createReply();
        outbox.setContent("Request join session " + sessionKey + " in " + ciudad_seleccionada);
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        if (!session.getContent().startsWith("Confirm")) {
            Error("Could not join session " + sessionKey + " due to " + session.getContent());
            return Status.CLOSEPROBLEM;
        }

        this.doPrepareNPC(1, DEST.class);
        
        this.outbox = session.createReply(); 
        outbox.setContent("Query missions session " + sessionKey);
        this.LARVAsend(outbox);

        session = LARVAblockingReceive();
        this.getEnvironment().setExternalPerceptions(session.getContent());
        this.MyReadPerceptions();
        
        String mission = this.chooseMission();
        Info("Mission selected: " + mission);
        this.getEnvironment().setCurrentMission(mission);
        
        // Conseguir DEST 
         listaDEST = this.DFGetAllProvidersOf("TYPE DEST");
         for(int i = 0 ; i < listaDEST.size(); i++){
             if(this.DFHasService(listaDEST.get(i), "TYPE DEST")){
                 miDEST = listaDEST.get(i);
             }
         }        
        // Info(this.easyPrintPerceptions());

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
        
        goalActual = E.getCurrentGoal();
        StringTokenizer tokens = new StringTokenizer(goalActual);
        String primeraPalabra = tokens.nextToken();
        switch (primeraPalabra) {
            case "MOVEIN":
                String ciudad = tokens.nextToken();
                if (!startedGoal){
                    //Solicitar navegación asistida a la ciudad
                    Info("Requesting AUTONAV to " + ciudad);
                    outbox = session.createReply();
                    outbox.setContent("Request course in " + ciudad + " session " + sessionKey);
                    this.LARVAsend(outbox);
                    session = this.LARVAblockingReceive();
                    startedGoal = true;
                } 

                //Control de posibles errores
                if (session.getContent().startsWith("Failure") || session.getContent().startsWith("Refuse")){
                    Error("Could not enable AUTONAV to city due to " + session.getContent());
                    return Status.CLOSEPROBLEM;
                } 

                this.getEnvironment().setExternalPerceptions(session.getContent());

                if (G(E)) {
                    Info("The goal " + E.getCurrentGoal() + " is solved");
                    // booleano a false de nuevo
                    startedGoal = false;
                    E.setNextGoal();
                    return Status.SOLVEPROBLEM;
                }

                behaviour = this.AgPlan(E, A);

                if (behaviour == null || behaviour.isEmpty()) {
                    Alert("Found no plan to execute");
                    return Status.CLOSEPROBLEM;
                }
                else {
                    Info("Plan to execute: " + behaviour.toString());
                    while(!behaviour.isEmpty()) {
                        a = behaviour.get(0);
                        behaviour.remove(0);
                        Info("Executing " + a);
                        this.MyExecuteAction(a.getName());

                        if (!Ve(E)) {
                            this.Error("The agent is not alive " + E.getStatus());
                            return Status.CLOSEPROBLEM;
                        }
                    }
                }
                this.MyReadPerceptions();
                break;
                
            case "LIST":
                String tipoPersona = tokens.nextToken();
                myStatus = this.doQueryPeople(tipoPersona);
                E.setNextGoal();
                break;
                
            case "REPORT":
                report += ";"; 
                
                this.outboxRep = new ACLMessage();
                outboxRep.setSender(getAID());
                outboxRep.addReceiver(new AID(miDEST, AID.ISLOCALNAME));
                outboxRep.setContent(report);
                this.LARVAsend(outboxRep);
                Info("Sended report \"" + report + "\" to " + miDEST);

                openRep = LARVAblockingReceive();
                Info(miDEST + " says: " + openRep.getContent());
                content = openRep.getContent();
                if (content.equals("Confirm")) {
                    Message(miDEST + " has received the report" );
                } else {
                    Error(content);
                    return Status.CHECKOUT;
                }
                E.setNextGoal();
                break;
                
            default:
                return Status.CLOSEPROBLEM;

        }
        
        if (!E.getCurrentMission().isOver())
            return Status.SOLVEPROBLEM;
        return Status.CLOSEPROBLEM;
    }
    
    /**
    * @author Moisés Noguera Carrillo
    */
    
    public Status MyMoveIn(String ciudad) {
        
        
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
     
    public String easyPrintPerceptions() {
        String res;
        int matrix[][];

        if (getEnvironment() == null) {
            Error("Environment is unacessible, please setupEnvironment() first");
            return "";
        }
        res = "\n\nReading of sensors\n";
        if (E.getName() == null) {
            res += emojis.WARNING + " UNKNOWN AGENT";
            return res;
        } else {
            res += emojis.ROBOT + " " + E.getName();
        }
        res += "\n";
        res += String.format("%10s: %05d W\n", "ENERGY", E.getEnergy());
        res += String.format("%10s: %15s\n", "POSITION", E.getGPS().toString());
//        res += "PAYLOAD "+E.getPayload()+" m"+"\n";
        res += String.format("%10s: %05d m\n", "X", E.getGPS().getXInt())
                + String.format("%10s: %05d m\n", "Y", E.getGPS().getYInt())
                + String.format("%10s: %05d m\n", "Z", E.getGPS().getZInt())
                + String.format("%10s: %05d m\n", "MAXLEVEL", E.getMaxlevel())
                + String.format("%10s: %05d m\n", "MAXSLOPE", E.getMaxslope());
        res += String.format("%10s: %05d m\n", "GROUND", E.getGround());
        res += String.format("%10s: %05d º\n", "COMPASS", E.getCompass());
        if (E.getTarget() == null) {
            res += String.format("%10s: " + "!", "TARGET");
        } else {
            res += String.format("%10s: %05.2f m\n", "DISTANCE", E.getDistance());
            res += String.format("%10s: %05.2f º\n", "ABS ALPHA", E.getAngular());
            res += String.format("%10s: %05.2f º\n", "REL ALPHA", E.getRelativeAngular());
        }
        res += "\nVISUAL RELATIVE\n";
        matrix = E.getRelativeVisual();
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
        res += "LIDAR RELATIVE\n";
        matrix = E.getRelativeLidar();
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
        res += "Decision Set: " + A.toString() + "\n";
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
        outbox = open.createReply();
        outbox.setContent("Cancel session " + sessionKey);
        Info("Closing problem " + problem + ", session " + sessionKey);
        this.LARVAsend(outbox);
        inbox = LARVAblockingReceive();
        Info(problemManager + " says: " + inbox.getContent());
        this.doDestroyNPC();
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
        
        if (E.isTargetLeft()) {
            if (a.getName().equals("LEFT")) {
                nextWhichwall = "RIGHT";
                nextdistance = E.getDistance();
                nextPoint = E.getGPS();
                return Choice.ANY_VALUE;
            }
        } else {
            if (a.getName().equals("RIGHT")) {
                nextWhichwall = "LEFT";
                nextdistance = E.getDistance();
                nextPoint = E.getGPS();
                return Choice.ANY_VALUE;
            }
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
    
    /**
    * @author Carlos Galán Carracedo 26 de octubre
    */
    protected Status doQueryPeople(String type){
        Info("Querying people "+type);
        outbox = session.createReply();
        outbox.setContent("Query "+type.toUpperCase()+" session " + sessionKey);
        this.LARVAsend(outbox);
        session= LARVAblockingReceive();
        getEnvironment().setExternalPerceptions(session.getContent());
        if(report == ""){
        report += "REPORT;" + getEnvironment().getCurrentCity() + " " + type.toLowerCase() + " " + getEnvironment().getPeople().length ;            
        }else{
        report += " " + type.toLowerCase() + " " + getEnvironment().getPeople().length ;            
        }

//        Message("Found "+getEnvironment().getPeople().length+" "+type+" in "
//                +getEnvironment().getCurrentCity());
//        getEnvironment().getCurrentMission().nextGoal();
        
//        Message(report);
        return Status.SOLVEPROBLEM;
    }
    
}

