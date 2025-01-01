package yan.lx.bedrockminer.task;

public enum TaskState {
    INITIALIZE,
    WAIT_GAME_UPDATE,
    WAIT_CUSTOM_UPDATE,
    PLACE,
    EXECUTE,
    TIMEOUT,
    FAIL,
    BLOCK_ITEM_RECYCLE,
    COMPLETE
}
