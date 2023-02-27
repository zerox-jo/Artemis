package ${packageName};

import java.util.*;

public class BubbleSort implements SortStrategy {

    public static String literal1 = "Header";
    private static final String LITERAL_TWO = "Literal2";

    /**
     * Sorts dates with BubbleSort.
     *
     * @param input the List of Dates to be sorted
    */
    public void performSort(List<Date> input) {
        for (int i = input.size() - 1; i >= 0; i--) {
            for (int j = 0; j < i; j++) {
                if (input.get(j).compareTo(input.get(j + 1)) > 0) {
                    Date temp = input.get(j);
                    input.set(j, input.get(j + 1));
                    input.set(j + 1, temp);
                }
            }
        }

    }
}
