package android.os;

/**
 * @hide
 * @author clee
 * licx@OnLineRD.framework.oppo_service
 */
interface IOppoService {
	
    /******************************************************
     * 
     * This part is for writing raw partition
     * 
     ******************************************************/
    String readRawPartition(int offset, int size);
    int writeRawPartition(String content);
    /******************************************************
     * 
     * This part is for writing critical data
     * 
     ******************************************************/
    String readCriticalData(int id, int size);
    int writeCriticalData(int id, String content);
}