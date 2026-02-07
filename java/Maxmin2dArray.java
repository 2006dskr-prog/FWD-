import java.util.Scanner;
public class Maxmin2dArray {
    public static void main(String[] args) {
        try (Scanner sc = new Scanner(System.in)) {
            int m = sc.nextInt();
            int n = sc.nextInt();
            int[][] a = new int[m][n];
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    a[i][j] = sc.nextInt();
                }
            }
            int max = a[0][0];
            int min = a[0][0];
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    if (a[i][j] > max) {
                        max = a[i][j];
                    }
                    if (a[i][j] < min) {
                        min = a[i][j];
                    }
                }
            }
            System.out.println("Maximum value in the array: " + max);
            System.out.println("Minimum value in the array: " + min);
        }
    }
}