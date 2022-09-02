// mode=local,language=javascript,parameters=[op,lockName,lockOwner,timeout]

if (op === "lock") {
    var lockManager = cache.getAdvancedCache().getLockManager()
    var output = lockManager.getOwner(lockName)
    var lockPromise = lockManager.lock(lockName, lockOwner, timeout, java.util.concurrent.TimeUnit.MILLISECONDS)
    lockPromise.lock()
    "locked"
} else {
    cache.getAdvancedCache().getLockManager().unlock(lockName, lockOwner)
    "unlocked"
}
