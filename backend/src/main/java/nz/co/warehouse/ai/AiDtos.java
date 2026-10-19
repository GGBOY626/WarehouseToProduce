package nz.co.warehouse.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public final class AiDtos {
    private AiDtos() {}
    public record Scope(@Size(max=200) String query,
                        @Pattern(regexp="|RACK|AISLE") String kind,
                        @Pattern(regexp="|[A-M]{1,2}") String row, @Positive Long areaId,
                        @Pattern(regexp="|UPPER|MIDDLE|LOWER|LEFT_1|LEFT_2|RIGHT_1|RIGHT_2") String lane,
                        boolean unknownOnly, @Positive Long taskId, Boolean finished, boolean hideFinished) {
        public Scope(String query,String kind,String row,Long areaId,String lane,boolean unknownOnly,Long taskId){
            this(query,kind,row,areaId,lane,unknownOnly,taskId,null,false);
        }
    }
    public record Chat(@NotBlank @Size(max=2000) String message, @NotNull @Valid Scope scope) {}
    // Extra filters are always intersected with the server-held page scope.
    public record Filters(@Size(max=200) String query, @Size(max=100) String batch,
                          @Pattern(regexp="|[A-M]{1,2}") String row,
                          @Pattern(regexp="|RACK|AISLE") String kind,
                          @Pattern(regexp="|UPPER|MIDDLE|LOWER|LEFT_1|LEFT_2|RIGHT_1|RIGHT_2") String lane,
                          @Positive Long productId, boolean unknownOnly) {}
    public record ToolRequest(@NotBlank String ticket, @NotBlank String name, @NotNull @Valid Filters filters) {}
}
