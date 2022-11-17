package creative.market.service.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderCountCompareByPeriodRes {

    private List<String> dateList;
    private List<Long> myCountList;
    private List<Long> avgCountList;
}
