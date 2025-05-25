import java.util.HashSet;
import java.util.concurrent.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
public class Solution implements CommandRunner{ 

    private HashSet<Long> dependent = new HashSet<Long>(); //list of calculations that are in a circular dependency
    private final Map<Long, Thread> threads = new ConcurrentHashMap<>();//holds the threads for each calculation 
    private Map<Long,ArrayList<Long>> nextCalculations = new HashMap<>();//calculations scheduled to start
    private final Map<Long, Integer> resultMap = new HashMap<>();//results of completed calculations
    private final Map<Long, SlowCalculator> calculations = new HashMap<>();//calculations that have been requested

    public Solution(){}

    //takes in the requested command from the user
    public synchronized String runCommand(String command){
        String[] split = command.split(" ");
        String answer = "";
        try{
            if(split[0].equals("start")){
                if(split.length !=2) return "Invalid command";//checks that the command has been input correctly            
                else return start(Long.parseLong(split[1]));//starts a calculation
            }
            else if(split[0].equals("cancel")){
                if(split.length !=2) return "Invalid command";//checks that the command has been input correctly
                answer = cancelN(Long.parseLong(split[1]));//cancels a calculation
            }
            else if(split[0].equals("running")){
                if(split.length !=1) return "Invalid command";//checks that the command has been input correctly  
                answer = running();//checks what calculations are running
            }
            else if(split[0].equals("get")){
                if(split.length !=2) return "Invalid command";//checks that the command has been input correctly
                answer = getN(Long.parseLong(split[1]));//gets the answer
            }
            else if(split[0].equals("after")){
                if(split.length !=3) return "Invalid command";//checks that the command has been input correctly
                answer = afterNM(Long.parseLong(split[1]), Long.parseLong(split[2]), split[1], split[2]);//schedules a calculation to start
            }
            else if(split[0].equals("finish")){
                if(split.length !=1) return "Invalid command";//checks that the command has been input correctly  
                answer = finish();//finished the calculations
            }
            else if(split[0].equals("abort")){
                if(split.length !=1) return "Invalid command";//checks that the command has been input correctly  
                answer = abort();//aborts the calculations
            }
            else answer = "Invalid command";//if an incorrect command is issued this si returned
            return answer;
        }catch(NumberFormatException e){
            return "Invalid command";
        }
    };
    
    //starts a calculation
    private String start(Long N){
        Thread thread;//create a new thread
        if(calculations.keySet().contains(N)){//if a calculation has been scheduled to start
            thread = new Thread(calculations.get(N));//create a thread with the Slow Calculator that has already been created 
        }
        else{//if it has not already been scheduled
            SlowCalculator sCalc = new SlowCalculator(N.longValue(), this);//create a new Slow Calculator
            thread = new Thread(sCalc);//create a new thread
            calculations.put(N,sCalc);//add the calculation to the map of calculations
        }
        threads.put(N,thread);    
        thread.start();//start the thread

        return "started "+N;
    }

    //cancel a calculation
    private String cancelN(Long N){

        SlowCalculator calc = calculations.get(N);//get the instance of SlowCalculator for N
        if(calc!=null){
            calc.setCancelled(true); //set the status of the calculation to cancel           
            Thread thread = threads.remove(N);
            if (thread != null) {//if the thread has started
                thread.interrupt();//interrupt it
            }  
        }  

        // If N has not started:
        for (Map.Entry<Long, ArrayList<Long>> entry : nextCalculations.entrySet()) {//get all scheduled calculations
            ArrayList<Long> dependents = entry.getValue();
            Iterator<Long> it = dependents.iterator();
            while (it.hasNext()) {
                if (it.next().equals(N)) {
                    it.remove();//remove the scheduled calculation
                }
            }
        }

        // Start any calculations that were waiting for this one
        ArrayList<Long> waitingCalculations = nextCalculations.remove(N);
        if (waitingCalculations != null) {
            for (Long M : waitingCalculations) {
                start(M);
            }
        }
        return "cancelled "+N;
    }

    //returns the number of calculations currently running
    private String running(){
        int numRun = 0;
        ArrayList<Long> runningCalcs = new ArrayList<>();
        for(Long key:calculations.keySet()){
            if(!calculations.get(key).completed()&&!calculations.get(key).cancelled()&&calculations.get(key).isRunning()){
                //if the calculation is ongoing then add it to the count and to the list of calculations
                numRun++;
                runningCalcs.add(key);
            }
        }
        if(numRun==0) return "no calculations running";
        else{
            String toReturn = "";
            for(Long key:runningCalcs){
                toReturn = toReturn+" "+key;
            }
            return numRun+" calculations running:"+toReturn;
        }
    }

    //returns the result for the calculation for N if it exists
    private String getN(Long N){
        SlowCalculator sCalc = calculations.get(N);//get the slowcalculator for N
        if(sCalc==null) return "waiting";//if there is no calculation for N then it hasn't started yet         
        else if(sCalc.cancelled()) return "cancelled";//if the calculation has been cancelled        
        else if(sCalc.completed()) return "result is "+ resultMap.get(N);//gets the solution from resultMap using the key N
        else if(!sCalc.completed()) return "calculating";//if the calculation is still running
        else return "Invalid input";
    }

    private String afterNM(Long N, Long M, String stringN, String stringM){
    //schedule a calculation to start after N
        dependent = new HashSet<Long>();//reinitialise dependent to hold the values that are in a circular dependency
        //check if the calculation can start immediately
        if(calculations.get(N).completed()){
            start(M);//start the calculation for M
            return stringM+" will start after "+stringN;
        }
        //else check for circular dependency
        if(!checkForCircular(N, M, new ArrayList<Long>())){//check for circular dependency
            nextCalculations.putIfAbsent(N, new ArrayList<Long>());//if there are no calculations already scheduled after N, then add N to nextCalculations
            nextCalculations.get(N).add(M);//set M to start after N
            SlowCalculator sCalc = new SlowCalculator(M.longValue(), this);
            calculations.put(M,sCalc);
            return stringM+" will start after "+stringN;
        }
        else{                  
            String toReturn=String.format("circular dependency %d ", N);//initialise the string 
            for(Long entry:dependent){
                toReturn = toReturn + String.format("%d",entry);//add every value that is in the circular dependency to a string 
            }
            return toReturn;//return the string
        }
    
    }

    //find out if a calculation is waiting on another one to start
    private Long waitingOn(Long check){
        Long dependentOn = null;
        for(Long Key: nextCalculations.keySet()){
            if(nextCalculations.get(Key).contains(check)){//if check is waiting on Key to start
                dependentOn = Key;
                dependent.add(Key);//add the key to the list of dependencies
                break;
            }
        }
        return dependentOn;//return the calculation that check is waiting on to start
    }

    //checks for a circular dependency
    private boolean checkForCircular(Long check, Long M, ArrayList<Long> inChain){
        if(check.equals(M)) return true;//if two values are the same then there is circular dependency
        if(waitingOn(check)==null) return false;//if check isn't waiting on anything to start then there is no dependency 
        if(waitingOn(check).equals(M))return true;//if check is waiting on M to start then there is a dependency
        else{//check for longer dependencies 
            inChain.add(waitingOn(check));
            checkForCircular(waitingOn(check), M, inChain);
        }
        return false;
    }

    //wait for all calculation to finish then close it down 
    private String finish(){
        Set<Long> pendingCalculations = new HashSet<>();//calculations that haven't started
        for (Map.Entry<Long, ArrayList<Long>> entry : nextCalculations.entrySet()) {
            pendingCalculations.addAll(entry.getValue());//add all calculations that have been scheduled 
        }
        
        while (!nextCalculations.isEmpty() || !pendingCalculations.isEmpty()) {//while there are calculations that are scheduled
            Iterator<Long> it = pendingCalculations.iterator();
            while (it.hasNext()) {
                Long calcId = it.next();
                if (threads.containsKey(calcId)) {
                    it.remove();//if the calculation has been started then remove it
                }
            }
            
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "error";
            }
        }
        
        //wait for all threads to stop
        for (Thread thread : threads.values()) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "error";
            }
        }
        return "finished";
    }

    //abort all calculations
    private String abort(){
        nextCalculations.clear(); //clear all calculations that are scheduled so that none start after the ongoing calculations are cancelled     
        for(Thread thread : threads.values()){
            thread.interrupt();//interrupt all threads
        }
        calculations.clear();//clear all calculations
        return "aborted";
    }

    //allows SlowCalculator to return the result of the calculation for N
    public void setResult(Long N, int result){
        resultMap.put(N,(int)result);//enter the result for N int resultMap
        ArrayList<Long> doNext = nextCalculations.remove(N);//get all calculations that are scheduled to start after N
        if(doNext!=null){
            for(Long M: doNext){
                start(M);//start the calculations
            }
        }
    }


}