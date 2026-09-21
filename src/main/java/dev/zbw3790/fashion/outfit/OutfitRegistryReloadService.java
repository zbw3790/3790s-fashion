package dev.zbw3790.fashion.outfit;

import java.nio.file.Path;
import java.util.Objects;
import java.util.List;
import java.util.function.Function;
import dev.zbw3790.fashion.fashion.PlayerFashionService;

/** 命令之外可复用的重载入口；扫描期间 Live 保持可用，提交和发布均在服务器线程。 */
public final class OutfitRegistryReloadService {
    private final PlayerFashionService fashion;
    private final Path root;
    private final OutfitRegistryLoader loader;
    private final Runnable requireServerThread;
    public OutfitRegistryReloadService(PlayerFashionService fashion, Path root, Runnable requireServerThread) {
        this(fashion,root,new OutfitRegistryLoader(4096),requireServerThread);
    }
    OutfitRegistryReloadService(PlayerFashionService fashion, Path root, OutfitRegistryLoader loader, Runnable requireServerThread) {
        this.fashion=Objects.requireNonNull(fashion); this.root=Objects.requireNonNull(root);
        this.loader=Objects.requireNonNull(loader); this.requireServerThread=Objects.requireNonNull(requireServerThread);
    }
    public Result reload(Function<Publication, Clients> publish) {
        requireServerThread.run(); Objects.requireNonNull(publish);
        var candidate=loader.loadExisting(root);
        if (!candidate.knowledge().trustworthy()) return new Result(false,false,"装束根目录不可用或扫描不可信，保留当前目录和全部选择。",fashion.registryGeneration(),0,0,candidate.diagnostics());
        var armorCandidate=new dev.zbw3790.fashion.armor.ArmorRegistryLoader().loadExisting(root.resolveSibling("armor"));
        if (!armorCandidate.snapshot().available()) return Result.failed("盔甲根目录不可用或扫描不可信，两个领域都保留此前快照。");
        // 即使有效内容没有变化，管理员仍须看到本次坏样式隔离原因；每次命令有界。
        armorCandidate.diagnostics().stream().limit(16).forEach(message ->
                org.slf4j.LoggerFactory.getLogger("fashion_3790").warn("盔甲重载诊断：{}",message));
        var commit=fashion.reloadResources(candidate,armorCandidate);
        if (commit.status()==PlayerFashionService.ReloadStatus.UNAVAILABLE)
            return Result.failed("时装服务只读、已停止或运行期容量／版本耗尽，保留当前目录和全部选择。");
        if (commit.status()==PlayerFashionService.ReloadStatus.NO_CHANGE)
            return new Result(true,false,"装束与盔甲目录内容没有变化，未增加代次或重置连接预算。",fashion.registryGeneration(),0,0,candidate.diagnostics());
        Clients clients=publish.apply(new Publication(fashion.registryGeneration(),candidate,commit));
        String summary="装束与盔甲重载完成：代次="+fashion.registryGeneration()+"，可信定义="+candidate.registry().size()
                +"，无可信定义的已有 ID="+(candidate.knowledge().knownExistingIds().size()-candidate.registry().size())
                +"，盔甲定义="+armorCandidate.snapshot().entries().size()+"，盔甲隔离诊断="+armorCandidate.diagnostics().size()
                +"，诊断="+candidate.diagnostics().size()+"，保存变化玩家="+commit.storedChanges()
                +"，已刷新客户端="+clients.refreshed()+"，需重连的旧客户端="+clients.pinned()+"。";
        return new Result(true,true,summary,fashion.registryGeneration(),clients.refreshed(),clients.pinned(),candidate.diagnostics());
    }
    public record Publication(long generation, OutfitRegistryLoadResult candidate, PlayerFashionService.OutfitReloadCommit commit) { }
    public record Clients(int refreshed, int pinned) { }
    public record Result(boolean success, boolean changed, String message, long generation, int refreshed, int pinned, List<OutfitDiagnostic> diagnostics) {
        public Result { diagnostics=List.copyOf(diagnostics); }
        public static Result failed(String message) { return new Result(false,false,message,0,0,0,List.of()); }
    }
}
