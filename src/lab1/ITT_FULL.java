/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package starwars;
//package starwars;

import Environment.Environment;
import agents.BB1F;
import agents.DEST;
import agents.MTT;
import agents.DroidShip;
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
import java.util.Random;

/**
 *
 * @author Ana García Muñoz (Creación del agente ITT_FULL deliberativo)
 * @author Moisés Noguera Carrillo (Añadidos algunos métodos que faltaban del
 * agente deliverativo al crear la clase)
 */
public class ITT_FULL extends LARVAFirstAgent {

    private ACLMessage outboxRep;

    Choice recAct = new Choice("RECHARGE");

    enum Status {
        START,
        CHECKIN,
        OPENPROBLEM,
        SOLVEPROBLEM,
        CLOSEPROBLEM,
        CHECKOUT,
        EXIT,
        JOINSESSION,
        SELECTMISSION
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
            ciudadActual,
            ciudadDest,
            ciudad_seleccionada = "";
    ACLMessage open, session, openRep, rechargeResp,backupResp, respDest;
    String[] contentTokens, ciudades, personasCapturar;
    String[] problems = {"CoruscantSingle", "CoruscantApr", "CoruscantNot", "CoruscantSob", "CoruscantHon"};
    ArrayList<String> listaDEST;
    Random aleatorio = new Random();

    protected String whichWall, nextWhichwall;
    protected double distance, nextdistance;
    protected Point3D point, nextPoint;

    Plan behaviour = null;
    Environment Ei, Ef;
    Choice a;

    String goalActual;
    Boolean startedGoal = false;
    Boolean sendTransponderReq = false;
    
    /**
    * @author Ana García Muñoz (needRecharge)
    */
    public Plan AgPlan(Environment E, DecisionSet A) {

        Ei = E.clone();
        Plan p = new Plan();
        boolean recharge = needRecharge();

        for (int i = 0; i < Ei.getRange() / 2 - 2; i++) {
            if (recharge == true) {
                p.add(recAct);
                Info("El plan es: " + p);
                return p;

            } else {
                Ei.cache();
                if (!Ve(Ei)) {
                    return null;
                } else if (G(Ei)) {
                    return p;
                } else {
                    a = Ag(Ei, A);
                    if (a != null) {
                        p.add(a);
                        Ef = S(Ei, a);
                        Ei = Ef;
                    } else {
                        return null;
                    }
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
//        this.deactivateSequenceDiagrams();

        logger.onEcho();

        logger.onTabular();

        myStatus = Status.START;
        this.setupEnvironment();
        this.enableDeepLARVAMonitoring();
        A = new DecisionSet();
        A.addChoice(new Choice("MOVE")).
                addChoice(new Choice("LEFT")).
                //addChoice(new Choice("RECHARGE")).
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
            case SELECTMISSION:
                myStatus = SelectMission();
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

    /**
     * Returns an ArrayList with the names of all the NPC of the given
     * type in the session
     * 
     * @author Moisés Noguera Carrillo
     */
    public ArrayList<String> getDroidShipsOfType(String type) {

        ArrayList<String> droidShipList = new ArrayList<String>();
        ArrayList<String> droidShipListInSession = new ArrayList<String>();

        droidShipList = this.DFGetAllProvidersOf(type);

        for (int i = 0; i < droidShipList.size(); i++) {
            if (this.DFHasService(droidShipList.get(i), sessionKey)) {
                droidShipListInSession.add(droidShipList.get(i));
            }
        }

        return droidShipListInSession;

    }

    /**
     * Override the method to send transponder information
     * @author Moisés Noguera Carrillo
     */
    @Override
    public ACLMessage LARVAblockingReceive() {

        boolean exit = false;
        ACLMessage res = null;

        while (!exit) {
            res = super.LARVAblockingReceive();
            if (res.getContent().equals("TRANSPONDER") && res.getPerformative() == ACLMessage.QUERY_REF) {
                outbox = res.createReply();
                outbox.setPerformative(ACLMessage.INFORM);
                outbox.setContent(this.Transponder());
                LARVAsend(outbox);
            } else {
                exit = true;
            }
        }
        return res;
    }

    /**
     *
     * @author Javier Serrano Lucas
     * @author Ana García Muñoz (Parte de contacto con DEST)
     */
    public Status MyJoinSession() {
        Info("Querying CITIES");
        outbox = session.createReply();
        outbox.setPerformative(ACLMessage.QUERY_REF);
        outbox.setContent("Query CITIES session " + sessionKey);
        this.LARVAsend(outbox);
        session = LARVAblockingReceive();
        this.getEnvironment().setExternalPerceptions(session.getContent());

        //ciudades = getEnvironment().getExternalPerceptions().split(" "); 
        ciudades = getEnvironment().getCityList();
        ciudad_seleccionada = this.inputSelect("Please select the city to start: ", ciudades, ciudades[0]);

        this.resetAutoNAV();
        this.DFAddMyServices(new String[]{"TYPE ITT"});
        outbox = session.createReply();
        outbox.setPerformative(ACLMessage.REQUEST);
        outbox.setContent("Request join session " + sessionKey + " in " + ciudad_seleccionada);
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        if (!session.getContent().startsWith("Confirm")) {
            Error("Could not join session " + sessionKey + " due to " + session.getContent());
            return Status.CLOSEPROBLEM;
        }

        this.doPrepareNPC(1, DEST.class);
        this.doPrepareNPC(1, BB1F.class);
        this.doPrepareNPC(1,MTT.class);
        DroidShip.Debug();

        this.outbox = session.createReply();
        outbox.setPerformative(ACLMessage.QUERY_REF);
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
        for (int i = 0; i < listaDEST.size(); i++) {
            if (this.DFHasService(listaDEST.get(i), sessionKey)) {
                miDEST = listaDEST.get(i);
                Alert("FOUND AGENT DEST" + miDEST);
            }
        }

        Alert("FOUND " + this.getDroidShipsOfType("TYPE BB1F").size() + " OF TYPE BB1F");

        return Status.SOLVEPROBLEM;
    }

    @Override
    protected Choice Ag(Environment E, DecisionSet A) {
        if (G(E)) {
            return null;
        } else if (A.isEmpty()) {
            return null;
        } else {
            A = Prioritize(E, A);
            whichWall = nextWhichwall;
            point = nextPoint;
            return A.BestChoice();
        }
    }

    /**
     *
     * @author Javier Serrano Lucas 
     */
    @Override
    public Status SelectMission(){
        controller = LARVAblockingReceive();
        if(controller.getPerformative() == ACLMessage.REQUEST){
            E.makeCurrentMission(controller.getContent());
            this.MyReadPerceptions();
            resetAutoNAV();
            return Status.SOLVEPROBLEM;
        } else if (controller.getPerformative() == ACLMessage.CANCEL){
            return Status.CHECKOUT;
        } else {
            return Status.CHECKOUT;
        }
    }

    /**
     *
     * @author Moisés Noguera Carrillo (MOVEIN, MOVEBY)
     * @author Javier Serrano Lucas (Estructura general del método, CAPTURE)
     * @author Carlos Galán Carracedo (LIST, MOVEBY, TRANSFER)
     * @author Ana García Muñoz (REPORT)
     */
    public Status MySolveProblem() {

        String primeraPalabra = "";
        
        goalActual = E.getCurrentGoal();
        Info("CURRENT GOAL IS " + goalActual);
        StringTokenizer tokens = new StringTokenizer(goalActual);
        
        if (!goalActual.isEmpty())
            primeraPalabra = tokens.nextToken();
        
        switch (primeraPalabra) {
            case "MOVEIN":
                String ciudad = tokens.nextToken();
                if (!startedGoal) {
                    //Solicitar navegación asistida a la ciudad
                    Info("Requesting AUTONAV to " + ciudad);
                    outbox = session.createReply();
                    outbox.setPerformative(ACLMessage.REQUEST);
                    outbox.setContent("Request course in " + ciudad + " session " + sessionKey);
                    outbox.setConversationId(sessionKey);
                    outbox.setProtocol("DROIDSHIP");
                    this.LARVAsend(outbox);
                    session = this.LARVAblockingReceive();
                    startedGoal = true;
                }

                //Control de posibles errores
                if (session.getContent().startsWith("Failure") || session.getContent().startsWith("Refuse")) {
                    Error("Could not enable AUTONAV to city due to " + session.getContent());
                    return Status.CLOSEPROBLEM;
                }

                this.getEnvironment().setExternalPerceptions(session.getContent());

                if (G(E)) {
                    Info("The goal " + E.getCurrentGoal() + " is solved");
                    startedGoal = false;
                    E.setNextGoal();
                    return Status.SOLVEPROBLEM;
                }

                behaviour = this.AgPlan(E, A);

                if (behaviour == null || behaviour.isEmpty()) {
                    Alert("Found no plan to execute");
                    return Status.CLOSEPROBLEM;
                } else {
                    Info("Plan to execute: " + behaviour.toString());
                    while (!behaviour.isEmpty()) {
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
                outboxRep.setPerformative(ACLMessage.INFORM);
                outboxRep.setContent(report);
                this.LARVAsend(outboxRep);
                Info("Sended report \"" + report + "\" to " + miDEST);

                openRep = LARVAblockingReceive();
                Info(miDEST + " says: " + openRep.getContent());
                content = openRep.getContent();
                if (content.equals("Confirm")) {
                    Message(miDEST + " has received the report");
                } else {
                    Error(content);
                    return Status.CLOSEPROBLEM;
                }
                E.setNextGoal();
                break;
            case "CAPTURE":
                int numCapturas = Integer.parseInt(tokens.nextToken());
                String tipo = tokens.nextToken();
                myStatus = this.doQueryPeople(tipo);
                //if(tipo == "JEDI"){
                    personasCapturar = this.getEnvironment().getPeople();
                    String ciudadCaptura = tokens.nextToken();
                    String agBackup = "";
                    ArrayList<String> listaApoyoCaptura = getDroidShipsOfType("TYPE MTT");
                    boolean aceptado = false;
                    int i = 0;
                    while(!aceptado){
                        this.outbox = new ACLMessage();
                        outbox.setSender(getAID());
                        outbox.addReceiver(new AID(listaApoyoCaptura.get(i), AID.ISLOCALNAME));
                        outbox.setContent("BACKUP");
                        outbox.setPerformative(ACLMessage.REQUEST);
                        outbox.setConversationId(sessionKey);
                        outbox.setProtocol("DROIDSHIP");
                        outbox.setReplyWith("BackUp" + i);
                        this.LARVAsend(outbox);
                        
                        backupResp = LARVAblockingReceive();
                        Info(listaApoyoCaptura.get(i) + " says: " + backupResp.getContent());
                        if (backupResp.getPerformative() == ACLMessage.AGREE) {
                            agBackup = listaApoyoCaptura.get(i);
                            //Message("El agente " + agBackup + " viene a ayudarme");
                            backupResp = LARVAblockingReceive();
                            if (!(backupResp.getPerformative() == ACLMessage.INFORM)){
                                Info("ERROR: " + backupResp.getContent());
                            }
                            else{
                                aceptado = true;
                                Info("Aceptado");
                            }
                        }
                        else{
                            if (i == listaApoyoCaptura.size()) {
                                i = 0;
                                LARVAwait(5000);
                            }
                            else {
                                i += 1;
                            } 
                        }
                    
                    }
                    
                    Info("FUERAAAAAAAAAAAA");
                    
                    for(int k = 0; k < numCapturas;k++){
                    
                        outbox = session.createReply();
                        outbox.setPerformative(ACLMessage.REQUEST);
                        outbox.setContent("Request capture "+ personasCapturar[k] + " session " + sessionKey);
                        this.LARVAsend(outbox);
                        session = this.LARVAblockingReceive();
                        if (!session.getContent().startsWith("Inform")) {
                            Error("Unable to execute action capture" + " due to " + session.getContent());
                        }
                    }
                    
                    this.outbox = new ACLMessage();
                        outbox.setSender(getAID());
                        outbox.addReceiver(new AID(agBackup, AID.ISLOCALNAME));
                        outbox.setContent("BACKUP");
                        outbox.setPerformative(ACLMessage.CANCEL);
                        outbox.setConversationId(sessionKey);
                        outbox.setProtocol("DROIDSHIP");
                        outbox.setReplyWith("BackUp" + i);
                        this.LARVAsend(outbox);
                //}
                E.setNextGoal();
                break;
                
            case "MOVEBY":

                if (!sendTransponderReq) {
                    outbox = new ACLMessage();
                    outbox.setSender(getAID());
                    outbox.addReceiver(new AID(miDEST, AID.ISLOCALNAME));
                    outbox.setContent("TRANSPONDER");
                    outbox.setPerformative(ACLMessage.QUERY_REF);
                    outbox.setConversationId(sessionKey);
                    //Generar número aleatorio en el replywith
                    outbox.setReplyWith("id-" + aleatorio.nextInt(1000000));
                    outbox.setProtocol("DROIDSHIP");
                    this.LARVAsend(outbox);

                    respDest = this.LARVAblockingReceive();

                    if (respDest.getPerformative() != ACLMessage.INFORM) {
                        Error("Error: Transponder message not recieved dut to " + respDest.getContent());
                        return Status.CLOSEPROBLEM;
                    }

                    String aux;
                    String respuesta = respDest.getContent();
                    String[] prueba = respuesta.split("/");
                    Info(prueba[3]);
                    // Obtener la ciudad donde se encuentra DEST
                    prueba = prueba[3].split(" ");
                    ciudadDest = prueba[2];

                    Alert("DEST is in " + ciudadDest);
                    sendTransponderReq = true;
                }
                
                // Solicitar AUTONAV hasta la ciudad del DEST
                if (!startedGoal) {
                    //Solicitar navegación asistida a la ciudad
                    Info("Requesting AUTONAV to " + ciudadDest);
                    outbox = session.createReply();
                    outbox.setPerformative(ACLMessage.REQUEST);
                    outbox.setContent("Request course in " + ciudadDest + " session " + sessionKey);
                    outbox.setConversationId(sessionKey);
                    outbox.setProtocol("DROIDSHIP");
                    outbox.setReplyWith("id-" + aleatorio.nextInt(1000000));
                    this.LARVAsend(outbox);
                    session = this.LARVAblockingReceive();
                    startedGoal = true;
                }

                //Control de posibles errores
                if (session.getContent().startsWith("Failure") || session.getContent().startsWith("Refuse")) {
                    Error("Could not enable AUTONAV to city due to " + session.getContent());
                    return Status.CLOSEPROBLEM;
                }

                this.getEnvironment().setExternalPerceptions(session.getContent());

                // Viajar hasta la ciudad del DEST
                if (G(E)) {
                    Info("The goal " + E.getCurrentGoal() + " is solved");
                    startedGoal = false;
                    E.setNextGoal();
                    return Status.SOLVEPROBLEM;
                }

                behaviour = this.AgPlan(E, A);

                if (behaviour == null || behaviour.isEmpty()) {
                    Alert("Found no plan to execute");
                    return Status.CLOSEPROBLEM;
                } else {
                    Info("Plan to execute: " + behaviour.toString());
                    while (!behaviour.isEmpty()) {
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
                
            case "TRANSFER":
                
                Boolean jediTransferido;
                String jedisCapturados[] = this.getEnvironment().getCargo();
                int numJedis = this.getEnvironment().getPayload();
                
                Alert("JEDIS TO TRANSFER: " + numJedis);
                
                for(i=0; i<numJedis; i++){
                        jediTransferido = false;

                        while(!jediTransferido){
                                this.outbox = new ACLMessage();
                                outbox.setSender(getAID());
                                outbox.addReceiver(new AID(miDEST, AID.ISLOCALNAME));
                                outbox.setContent("TRANSFER " + jedisCapturados[i]);
                                outbox.setPerformative(ACLMessage.QUERY_REF);
                                outbox.setConversationId(sessionKey);
                                outbox.setProtocol("DROIDSHIP");
                                outbox.setReplyWith("id-" + aleatorio.nextInt(1000000));
                                this.LARVAsend(outbox);

                                inbox = this.LARVAblockingReceive();

                                if(inbox.getPerformative() == ACLMessage.INFORM)
                                        jediTransferido = true;
                        }
                }

                E.setNextGoal();

                break;

            default:
                return Status.CLOSEPROBLEM;

        }

        if (!E.getCurrentMission().isOver()) {
            return Status.SOLVEPROBLEM;
        }
        else {
            Alert("Problem " + problem + " is solved!" );
        }

        return Status.CLOSEPROBLEM;
    }

    public boolean MyReadPerceptions() {
        Info("Reading perceptions...");
        outbox = session.createReply();
        outbox.setPerformative(ACLMessage.QUERY_REF);
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

    /**
     *
     * @author Ana García Muñoz
     */
    public boolean needRecharge() {
        outbox = session.createReply();
        outbox.setContent("Query sensors session " + sessionKey);
        outbox.setPerformative(ACLMessage.QUERY_REF);
        outbox.setConversationId(sessionKey);
        outbox.setProtocol("DROIDSHIP");
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        getEnvironment().setExternalPerceptions(session.getContent());

        int energy = getEnvironment().getEnergy();
        boolean needed = false;

        if (energy < 150 && energy > 90) {
            needed = true;
            //Message("RECARGA PORQUE TIENES " + energy + " DE ENERGÍA");
        }

        return needed;
    }

    /**
     * @author Ana García Muñoz (tratar la accion RECHARGE)
     */
    public boolean MyExecuteAction(String action) {
        if (action == "RECHARGE") {
            ArrayList<String> listaRec = getDroidShipsOfType("TYPE BB1F");
            String agRecarga = "";
            boolean aceptado = false;
            int i = 0;
            while (!aceptado) {
                this.outbox = new ACLMessage();
                outbox.setSender(getAID());
                outbox.addReceiver(new AID(listaRec.get(i), AID.ISLOCALNAME));
                outbox.setContent("REFILL");
                outbox.setPerformative(ACLMessage.REQUEST);
                outbox.setConversationId(sessionKey);
                outbox.setProtocol("DROIDSHIP");
                outbox.setReplyWith("Recharge" + i);
                this.LARVAsend(outbox);

                rechargeResp = LARVAblockingReceive();
                Info(listaRec.get(i) + " says: " + rechargeResp.getContent());
                if (rechargeResp.getPerformative() == ACLMessage.AGREE) {
                    agRecarga = listaRec.get(i);
                    //Message("El agente " + agRecarga + " viene a ayudarme");
                    rechargeResp = LARVAblockingReceive();
                    if (!(rechargeResp.getPerformative() == ACLMessage.INFORM)){
                        Info("ERROR: " + rechargeResp.getContent());
                        return false;
                    }
                    aceptado = true;
                } else {
                    if (i == listaRec.size()) {
                        i = 0;
                    } else {
                        i += 1;
                    }

                }
            }
//            Message("HE salido del while");
//            // Cuando termine de recargar
//            this.MySolveProblem();

        } else {

            Info("Executing action " + action);
            outbox = session.createReply();
            outbox.setPerformative(ACLMessage.REQUEST);
            outbox.setContent("Request execute " + action + " session " + sessionKey);
            this.LARVAsend(outbox);
            session = this.LARVAblockingReceive();
            if (!session.getContent().startsWith("Inform")) {
                Error("Unable to execute action " + action + " due to " + session.getContent());
                return false;
            }
        }

        return true;
    }

    /**
     *
     * @author Carlos Galán Carracedo (añadida la destrucción del NPC)
     */
    public Status MyCloseProblem() {
        outbox = open.createReply();
        outbox.setPerformative(ACLMessage.CANCEL);
        outbox.setContent("Cancel session " + sessionKey);
        outbox.setConversationId(sessionKey);
        Info("Closing problem " + problem + ", session " + sessionKey);
        this.LARVAsend(outbox);
        inbox = LARVAblockingReceive();
        Info(problemManager + " says: " + inbox.getContent());
        this.doDestroyNPC();
        return Status.CHECKOUT;
    }

    protected double goAhead(Environment E, Choice a) {
        if (a.getName().equals("MOVE")) {
            return U(S(E, a));
        } else {
            return U(S(E, a), new Choice("MOVE"));
        }
    }

    /**
     *
     * @author Carlos Galán Carracedo
     * @author Moisés Noguera Carrillo Añadida heurística para que en el caso de
     * que haya un obstáculo y el objetivo esté a la izquierda el agente rodee
     * por la izquierda y en el caso de que esté a la derecha lo haga por ese
     * lado. Anteriormente siempre se rodeaba por la derecha.
     */
    public double goAvoid(Environment E, Choice a) {

        if (E.isTargetRight()) {
            if (a.getName().equals("RIGHT")) {
                nextWhichwall = "LEFT";
                nextdistance = E.getDistance();
                nextPoint = E.getGPS();
                return Choice.ANY_VALUE;
            }
        } else {
            if (a.getName().equals("LEFT")) {
                nextWhichwall = "RIGHT";
                nextdistance = E.getDistance();
                nextPoint = E.getGPS();
                return Choice.ANY_VALUE;
            }
        }

        return Choice.MAX_UTILITY;
    }

    /**
     *
     * @author Moisés Noguera Carrillo
     */
    public double turnBack(Environment E, Choice a) {
        if (a.getName().equals("RIGHT")) {
            return Choice.ANY_VALUE;
        } else {
            return Choice.MAX_UTILITY;
        }
    }

    @Override
    protected double U(Environment E, Choice a) {
        if (whichWall.equals("LEFT")) {
            return goFollowWallLeft(E, a);
        } else if (whichWall.equals("RIGHT")) {
            return goFollowWallRight(E, a);
        } else if (!E.isFreeFront()) {
            return goAvoid(E, a);
        } else if (E.isTargetBack()) {
            return turnBack(E, new Choice("RIGHT"));
        } else {
            return goAhead(E, a);
        }
    }

    public double goFollowWallLeft(Environment E, Choice a) {
        Info("Siguiendo pared izquierda");
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

    public double goFollowWallRight(Environment E, Choice a) {
        Info("Siguiendo pared derecha");
        if (E.isFreeFrontRight()) {
            return goTurnOnWallRight(E, a);
        } else if (E.isTargetFrontLeft()
                && E.isFreeFrontLeft()
                && E.getDistance() < point.planeDistanceTo(E.getTarget())) {
            return goStopWallRight(E, a);
        } else if (E.isFreeFront()) {
            return goKeepOnWall(E, a);
        } else {
            return goRevolveWallRight(E, a);
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

    public double goTurnOnWallRight(Environment E, Choice a) {
        if (a.getName().equals("RIGHT")) {
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

    public double goRevolveWallRight(Environment E, Choice a) {
        if (a.getName().equals("LEFT")) {
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

    public double goStopWallRight(Environment E, Choice a) {
        if (a.getName().equals("LEFT")) {
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
     * @author Carlos Galán Carracedo
     * @author Ana García Múñoz (parte asociada al mensaje de REPORT)
     */
    protected Status doQueryPeople(String type) {
        Info("Querying people " + type);
        outbox = session.createReply();
        outbox.setPerformative(ACLMessage.QUERY_REF);
        outbox.setContent("Query " + type.toUpperCase() + " session " + sessionKey);
        this.LARVAsend(outbox);
        session = LARVAblockingReceive();
        getEnvironment().setExternalPerceptions(session.getContent());

        if (report == "") {
            report += "REPORT;" + getEnvironment().getCurrentCity() + " " + type.toLowerCase() + " " + getEnvironment().getPeople().length;
            ciudadActual = getEnvironment().getCurrentCity();
        } else {
            if (!getEnvironment().getCurrentCity().equals(ciudadActual)) {
                report += ";" + getEnvironment().getCurrentCity();
                ciudadActual = getEnvironment().getCurrentCity();
            }

            report += " " + type.toLowerCase() + " " + getEnvironment().getPeople().length;
        }

//        Message("Found "+getEnvironment().getPeople().length+" "+type+" in "
//                +getEnvironment().getCurrentCity());
//        getEnvironment().getCurrentMission().nextGoal();
//        Message(report);
        return Status.SOLVEPROBLEM;
    }

}
