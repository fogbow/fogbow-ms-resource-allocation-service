package cloud.fogbow.ras.core.plugins.interoperability.azure.attachment.sdk;

import java.util.concurrent.ExecutorService;

import org.apache.log4j.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.microsoft.azure.management.compute.VirtualMachine;

import cloud.fogbow.ras.constants.Messages;
import cloud.fogbow.ras.core.plugins.interoperability.azure.util.AzureSchedulerManager;
import rx.Observable;
import rx.Scheduler;
import rx.schedulers.Schedulers;

public class AzureAttachmentOperationSDK {

    private static final Logger LOGGER = Logger.getLogger(AzureAttachmentOperationSDK.class);

    private Scheduler scheduler;
    
    public AzureAttachmentOperationSDK() {
        ExecutorService executor = AzureSchedulerManager.getVolumeExecutor();
        this.scheduler = Schedulers.from(executor);
    }
    
    public void subscribeAttachDiskFrom(Observable<VirtualMachine> observable) {
        setAttachDiskBehaviour(observable)
        .subscribeOn(this.scheduler)
        .subscribe();
    }

    @VisibleForTesting
    Observable<VirtualMachine> setAttachDiskBehaviour(Observable<VirtualMachine> observable) {
        return observable.onErrorReturn((error -> {
            LOGGER.error(Messages.Error.ERROR_ATTACH_DISK_ASYNC_BEHAVIOUR, error);
            return null;
        })).doOnCompleted(() -> {
            LOGGER.info(Messages.Info.END_ATTACH_DISK_ASYNC_BEHAVIOUR);
        });
    }
    
    public void subscribeDetachDiskFrom(Observable<VirtualMachine> observable) {
        setDetachDiskBehaviour(observable)
        .subscribeOn(this.scheduler)
        .subscribe();
    }

    @VisibleForTesting
    Observable<VirtualMachine> setDetachDiskBehaviour(Observable<VirtualMachine> observable) {
        return observable.onErrorReturn((error -> {
            LOGGER.error(Messages.Error.ERROR_DETACH_DISK_ASYNC_BEHAVIOUR, error);
            return null;
        })).doOnCompleted(() -> {
            LOGGER.info(Messages.Info.END_DETACH_DISK_ASYNC_BEHAVIOUR);
        });
    }

    @VisibleForTesting
    void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }
}