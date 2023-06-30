# 6.830 Lab 3: Query Optimization

**Assigned: Wednesday, Mar 17, 2021**
**Due: Tuesday, Apr 6, 2021**

## 1.1. Implementation hints

在这个实验室中，你将在SimpleDB之上实现一个查询优化器。主要任务包括实现一个选择性估计框架和一个基于成本的优化器。你可以自由决定具体实现什么，但我们建议使用类似于课堂上讨论的Selinger基于成本的优化器（第9讲）。

本文件的其余部分描述了添加优化器支持所涉及的内容，并提供了一个基本的概要，说明你如何这样做。

与之前的实验室一样，我们建议你尽可能早地开始。

我们建议沿着这份文件进行练习，以指导你的实施，但你可能会发现不同的顺序对你更有意义。和以前一样，我们将通过查看你的代码并验证你是否通过了蚂蚁目标测试和systemtest的测试来给你的作业评分。关于评分和你需要通过的测试的完整讨论见第3.4节。

下面是你可能进行这个实验的一种方式的粗略轮廓。关于这些步骤的更多细节将在下面第2节给出。

- 使用直方图（IntHistogram类提供的骨架）或你设计的其他形式的统计数据，实现TableStats类中的方法，使其能够估计过滤器的选择性和扫描的成本。
- 实现JoinOptimizer类中的方法，使其能够估计连接的成本和选择性。
- 编写JoinOptimizer中的orderJoins方法。这个方法必须为一系列的连接产生一个最佳的顺序（可能使用Selinger算法），给定前两个步骤中计算的统计数据。

## 2. Optimizer outline

回顾一下，基于成本的优化器的主要思想是：：

- 使用关于表的统计数据来估计不同查询计划的 "成本"。通常，一个计划的成本与中间连接和选择的cardinalities（产生的图元数量）以及过滤器和连接谓词的选择性有关。
- 利用这些统计数据，以最佳方式排列连接和选择，并从几个备选方案中选择连接算法的最佳实现。
  在本实验中，你将实现代码来执行这两个功能。

优化器将从simpledb/Parser.java中调用。在开始本实验之前，你可能希望回顾一下实验2的解析器练习。简而言之，如果你有一个描述你的表的目录文件 catalog.txt，你可以通过输入来运行解析器：

```
java -jar dist/simpledb.jar parser catalog.txt
```

当解析器被调用时，它将计算所有表的统计数据（使用你提供的统计代码）。当一个查询被发出时，解析器将把查询转换成逻辑计划表示，然后调用你的查询优化器来生成一个最佳计划。

### 2.1 Overall Optimizer Structure

在开始实施之前，你需要了解SimpleDB优化器的整体结构。分析器和优化器的SimpleDB模块的整体控制流程如图1所示。

[![img](https://github.com/CreatorsStack/CreatorDB/raw/master/doc/controlflow.png)](https://github.com/CreatorsStack/CreatorDB/blob/master/doc/controlflow.png)
*Figure 1: Diagram illustrating classes, methods, and objects used in the parser*

底部的钥匙解释了这些符号；你将实现带有双边框的组件。这些类和方法将在后面的文字中得到更详细的解释（你可能希望回过头来看看这个图），但基本操作如下：

- Parser.java在初始化时构建了一组表的统计数据（存储在statsMap容器中）。然后它等待一个查询的输入，并对该查询调用parseQuery方法。
- parseQuery首先构造一个代表解析查询的LogicalPlan，然后在它构造的LogicalPlan实例上调用physicalPlan方法。physicalPlan 方法返回一个 DBIterator 对象，该对象可用于实际运行查询。

在接下来的练习中，你将实现帮助 physicalPlan 设计一个最佳计划的方法。

### 2.2. Statistics Estimation

准确地估计计划成本是相当棘手的。在这个实验室中，我们将只关注连接序列和基本表访问的成本。我们不会担心访问方法的选择（因为我们只有一种访问方法，即表扫描），也不会担心额外运算符（如聚合）的成本。

在这个实验中，你只需要考虑左深层计划。参见第2.3节，了解你可能实现的额外的 "奖励 "优化器功能，包括处理杂乱计划的方法。

#### 2.2.1 Overall Plan Cost

我们将以p=t1 join t2 join ... tn的形式来写连接计划，这表示一个左深连接，其中t1是最左边的连接（树中最深的）。给定一个像p这样的计划，其成本可以表示为：

```
scancost(t1) + scancost(t2) + joincost(t1 join t2) +
scancost(t3) + joincost((t1 join t2) join t3) +
... 
```

这里，scancost(t1)是扫描表t1的I/O成本，joincost(t1,t2)是连接t1和t2的CPU成本。为了使I/O和CPU成本具有可比性，通常使用一个恒定的比例因子，例如：

```
cost(predicate application) = 1
cost(pageScan) = SCALING_FACTOR x cost(predicate application)
```

在这个实验中，你可以忽略缓存的影响（例如，假设对表的每一次访问都会产生全部的扫描成本）--同样，这也是你可以在第2.3节中作为一个可选的额外扩展添加到实验中的东西。因此，scancost(t1)只是t1的页数x SCALING_FACTOR。

#### 2.2.2 Join Cost

当使用嵌套循环连接时，记得两个表t1和t2（其中t1是外表）之间的连接成本是简单的：

```
joincost(t1 join t2) = scancost(t1) + ntups(t1) x scancost(t2) //IO cost
                       + ntups(t1) x ntups(t2)  //CPU cost
```

Here, `ntups(t1)` is the number of tuples in table t1.

#### 2.2.3 Filter Selectivity

`ntups`可以通过扫描一个基表直接计算出来。对于一个有一个或多个选择谓词的表来说，估计ntups可能比较棘手--这就是过滤器的选择性估计问题。下面是你可能使用的一种方法，基于计算表中的值的直方图：

- 计算表中每个属性的最小和最大值（通过扫描一次）。
- 为表中的每个属性构建一个直方图。一个简单的方法是使用一个固定数量的桶NumB，每个桶代表直方图属性域的固定范围内的记录数。例如，如果一个字段f的范围是1到100，有10个桶，那么桶1可能包含1到10之间的记录数，桶2包含11到20之间的记录数，以此类推。
- 再次扫描该表，选择出所有图元的所有字段，用它们来填充每个直方图中的桶的计数。
- 为了估计平等表达式的选择性，f=const，计算包含值const的桶。假设桶的宽度（值的范围）是w，高度（图元的数量）是h，表中图元的数量是ntups。那么，假设值在整个桶中是均匀分布的，表达式的选择性大致为(h/w)/ntups，因为(h/w)代表了桶中值为常数的图元的预期数量。
- 为了估计一个范围表达式f>const的选择性，计算const所在的桶b，其宽度为w_b，高度为h_b。那么，b包含全部图元的一部分b_f = h_b / ntups。假设图元均匀地分布在整个b中，b中大于const的部分b_part是（b_right - const）/ w_b，其中b_right是b的桶的右端点。因此，b桶对谓词贡献了（b_f x b_part）的选择性。此外，b+1...NumB-1桶贡献了它们所有的选择性（可以用类似于上面b_f的公式来计算）。将所有桶的选择性贡献相加，将产生表达式的整体选择性。图2说明了这个过程。
- 涉及小于的表达式的选择性可以与大于的情况类似，看下到0的桶。

[![img](https://github.com/CreatorsStack/CreatorDB/raw/master/doc/lab3-hist.png)](https://github.com/CreatorsStack/CreatorDB/blob/master/doc/lab3-hist.png)
*Figure 2: Diagram illustrating the histograms you will implement in Lab 5*

在接下来的两个练习中，你将用代码来执行连接和过滤器的选择性估计。

------

**Exercise 1: IntHistogram.java**

你将需要实现一些方法来记录表的统计数据，以便进行选择性估计。我们提供了一个骨架类，IntHistogram，它可以做到这一点。我们的目的是让你使用上面描述的基于桶的方法来计算直方图，但你也可以自由地使用其他方法，只要它能提供合理的选择性估计。

我们提供了一个StringHistogram类，它使用IntHistogram来计算字符串谓词的选择度。如果你想实现一个更好的估计方法，你可以修改StringHistogram，尽管你不需要为了完成这个实验而修改。

完成这个练习后，你应该能够通过IntHistogramTest单元测试（如果你选择不实现基于直方图的选择性估计，则不要求你通过这个测试）。

------

**Exercise 2: TableStats.java**

TableStats类包含了计算一个表中图元和页数的方法，以及估计该表字段上的谓词的选择性的方法。我们创建的查询分析器为每个表创建一个TableStats的实例，并将这些结构传递给你的查询优化器（在后面的练习中你会需要它）。

你应该在TableStats中填写以下方法和类：

- 实现TableStats构造函数：一旦你实现了跟踪统计的方法，如直方图，你应该实现TableStats构造函数，添加代码来扫描表（可能是多次）以建立你需要的统计。
- 实现 estimateSelectivity(int field, Predicate.Op op、字段常数）：使用你的统计数据（例如，根据字段的类型，使用IntHistogram或StringHistogram），估计表上predicate字段op常数的选择率。
- 实现 estimateScanCost()：这个方法估计了顺序扫描文件的成本，鉴于读取一个页面的成本costPerPageIO。你可以假设没有寻道，也没有页面在缓冲池中。这个方法可以使用你在构造函数中计算的成本或大小。
- 实现 estimateTableCardinality(doubleselectivityFactor）：该方法返回关系中图元的数量，考虑到应用了具有选择性的selectivityFactor的谓词。这个方法可以使用你在构造函数中计算的成本或大小。

你可能希望修改TableStats.java的构造函数，例如，为了选择性估计的目的，计算上述字段的直方图。

完成这些任务后，你应该能够通过TableStatsTest中的单元测试。.

------

#### 2.2.4 Join Cardinality

最后，观察一下，上面的连接计划p的成本包括joincost((t1 join t2) joint3).为了评估这个表达式，你需要一些方法来估计t1 join t2的大小（ntups）。这个连接cardinality估计问题比过滤器的选择性估计问题更难。在这个实验室中，你不需要为此做任何花哨的事情，尽管第2.4节中的一个可选的练习包括一个基于直方图的连接选择性估计的方法。

在实现你的简单解决方案时，你应该牢记以下几点：

- 对于等价连接，当其中一个属性是主键时，由连接产生的图元的数量不能大于非主键属性的cardinality。
- 对于没有主键的等价连接，很难说输出的大小是什么--它可能是表的cardinality的乘积（如果两个表的所有图元都有相同的值）--或者它可能是0。
- 对于范围扫描，同样也很难对大小有什么准确的说法。输出的大小应该与输入的大小成正比。假设交叉产品的一个固定部分是由范围扫描发出的（比如说，30%），是可以的。一般来说，范围连接的成本应该大于相同大小的两个表的非主键平等连接的成本。

------

**Exercise 3: Join Cost Estimation**

JoinOptimizer.java类包括所有用于排序和计算连接成本的方法。在这个练习中，你将写出用于估计连接的选择性和成本的方法，特别是：

- 实现estimateJoinCost(LogicalJoinNode j, int card1, int card2, doublecost1, double cost2）：这个方法估计了连接j的成本，考虑到左边的输入是cardinality card1，右边的输入是cardinality card2，扫描左边输入的成本是cost1，而访问右边输入的成本是card2。你可以假设这个连接是一个NL连接，并应用前面提到的公式。
- 实现 estimateJoinCardinality(LogicalJoinNode j, intcard1, int card2, boolean t1pkey, boolean t2pkey）：这个方法估计了由连接j输出的图元的数量，给定左边的输入是大小为card1，右边的输入是大小为card2，以及指示左边和右边（分别）字段是否唯一（主键）的标志t1pkey和t2pkey。

实现这些方法后，你应该能够通过JoinOptimizerTest.java中的单元测试 estimateJoinCostTest 和 estimateJoinCardinality。

------

### 2.3 Join Ordering

现在你已经实现了估计成本的方法，你将实现Selinger优化器。对于这些方法，连接被表达为一个连接节点的列表（例如，对两个表的谓词），而不是课堂上描述的连接关系的列表。

将讲座中给出的算法转换为上面提到的连接节点列表形式，伪代码的大纲是：

```
1. j = set of join nodes
2. for (i in 1...|j|):
3.     for s in {all length i subsets of j}
4.       bestPlan = {}
5.       for s' in {all length d-1 subsets of s}
6.            subplan = optjoin(s')
7.            plan = best way to join (s-s') to subplan
8.            if (cost(plan) < cost(bestPlan))
9.               bestPlan = plan
10.      optjoin(s) = bestPlan
11. return optjoin(j)
```

为了帮助你实现这个算法，我们提供了几个类和方法来帮助你。首先，JoinOptimizer.java中的enumerateSubsets(List v, int size)方法将返回一个大小为v的所有子集的集合。这个方法对于大型集合来说效率非常低；你可以通过实现一个更有效的枚举器来获得额外的分数（提示：考虑使用原地生成算法和懒惰迭代器（或流）接口来避免物化整个幂集）。

第二，我们已经提供了方法：

```
    private CostCard computeCostAndCardOfSubplan(Map<String, TableStats> stats, 
                                                Map<String, Double> filterSelectivities, 
                                                LogicalJoinNode joinToRemove,  
                                                Set<LogicalJoinNode> joinSet,
                                                double bestCostSoFar,
                                                PlanCache pc) 
```

给出一个连接的子集（joinSet），以及一个要从这个子集中移除的连接（joinToRemove），这个方法计算出将joinToRemove连接到joinSet的最佳方法--{joinToRemove}。它在一个CostCard对象中返回这个最佳方法，其中包括成本、cardinality和最佳连接顺序（作为一个列表）。如果找不到计划（因为，例如，没有可能的左深连接），或者如果所有计划的成本都大于bestCostSoFar参数，computeCostAndCardOfSubplan可能返回null。该方法使用了一个叫做pc的先前连接的缓存（在上面的psuedocode中是optjoin）来快速查找joinSet的最快方式--
{joinToRemove}。其他参数（stats和filterSelectivities）被传递到你必须实现的orderJoins方法中，作为练习4的一部分，下面会有解释。这个方法基本上是执行前面描述的假代码的第6-8行。

第三，我们提供了这个方法：

```
    private void printJoins(List<LogicalJoinNode> js, 
                           PlanCache pc,
                           Map<String, TableStats> stats,
                           Map<String, Double> selectivities)
```

这种方法可以用来显示连接计划的图形表示（例如，当通过优化器的"-explain "选项设置 "explain "标志时）。

第四，我们提供了一个PlanCache类，可以用来缓存到目前为止在你实现Selinger时考虑的连接子集的最佳方式（使用computeCostAndCardOfSubplan需要这个类的一个实例）。

------

**Exercise 4: Join Ordering**

In `JoinOptimizer.java`, implement the method:

```
  List<LogicalJoinNode> orderJoins(Map<String, TableStats> stats, 
                   Map<String, Double> filterSelectivities,  
                   boolean explain)
```

这个方法应该在joins类成员上操作，返回一个新的List，这个List指定了应该进行的连接的顺序。这个列表中的第0项表示左深计划中最左、最底的连接。返回的列表中相邻的连接应该至少共享一个字段以确保计划是左深的。这里stats是一个对象，让你找到出现在查询的FROM列表中的给定表名的TableStats。 filterSelectivities让你找到表上任何谓词的选择性；它保证在FROM列表中的每个表名有一个条目。最后，explain指定了你应该输出一个连接顺序的表示，以供参考。

你可能希望使用上面描述的辅助方法和类来帮助你实现。大致上，你的实现应该遵循上面的假设代码，通过子集大小、子集和子集的子计划进行循环，调用 computeCostAndCardOfSubplan 并建立一个 PlanCache 对象来存储执行每个子集连接的最小成本方式。

实现这个方法后，你应该能够通过JoinOptimizerTest中的所有单元测试。你也应该通过系统测试QueryTest。