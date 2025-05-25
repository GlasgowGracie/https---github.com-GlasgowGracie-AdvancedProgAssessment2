
public class SlowCalculator implements Runnable {
    private final long N;
    private int result;
    private Solution s;
    private boolean completed;
    private boolean cancelled;
    private boolean currentlyRunning;
    
    public SlowCalculator(final long N, Solution s) {
        this.N = N;
        this.s = s;
        this.cancelled = false;
        this.completed = false;
        this.currentlyRunning = false;
    }

    public void run() {
        try {
            currentlyRunning = true;
            result = calculateNumFactors(N);
            s.setResult(N, result);//once the calculation is complete then set the result in Solution
            completed = true;//set completed to true
        } catch (InterruptedException e) {//if the thread is interrupted
            cancelled = true;//set cancelled to true
            Thread.currentThread().interrupt();
        }
    }

    private int calculateNumFactors(final long N) throws InterruptedException{
        int count = 0;
        long absN = Math.abs(N);
        for (long candidate = 2; candidate < absN; ++candidate) {
            if (Thread.currentThread().isInterrupted()) {//check for interruptions
                throw new InterruptedException("Calculation was interrupted");
            }
            if (isPrime(candidate) && absN % candidate == 0) {
                count++;
            }
        }
        
        return count;
    }

    private static boolean isPrime(final long n) {
        // This method should not be modified 
        for (long candidate = 2; candidate < Math.sqrt(n) + 1; ++candidate) {
            if (n % candidate == 0) {
                return false;
            }
        }
        return true;
    }

    public boolean completed(){
        return completed;
    }
    public boolean cancelled(){
        return cancelled;
    }

    public boolean isRunning(){
        return currentlyRunning;
    }

    public void setCancelled(boolean cancelled){//allows the calculation to be cancelled
        this.cancelled = cancelled;
    }
}