
package lab1;

import appboot.LARVABoot;

public class Main {

    public static void main(String[] args) {
        LARVABoot boot = new LARVABoot();
        boot.Boot("isg2.ugr.es", 1099);
        boot.launchAgent("Dookoo", AT_ST_FULL.class);
        boot.WaitToShutDown();
    }
    
}
