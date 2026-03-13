# 6.830 Lab 3：查询优化

**发布时间：2021 年 3 月 17 日，周三**<br>
**截止时间：2021 年 4 月 6 日，周二**

在本实验中，你将在 SimpleDB 之上实现一个查询优化器。主要任务包括实现一个选择率估计框架，以及一个基于代价的优化器。你可以自行决定具体实现细节，但我们建议你实现一个类似课堂上（第 9 讲）介绍的 Selinger 风格基于代价的优化器。

本文档的其余部分将说明如何为系统加入优化器支持，并给出一个基本实现路线。

和之前的实验一样，我们建议你尽早开始。


##  1. 开始

你应当从自己在 Lab 2 中提交的代码开始。（如果你没有提交 Lab 2，或你的解答无法正常工作，请联系助教讨论可选方案。）

本实验额外提供了一些测试用例和源码文件，这些在你最初收到的代码分发包中并不存在。我们同样鼓励你在我们提供的测试之外，自己再编写测试套件。

你需要把这些新文件加入到当前代码中。最简单的做法是进入你的项目目录（通常叫做 `simple-db-hw`），然后从主 GitHub 仓库拉取：

```
$ cd simple-db-hw
$ git pull upstream master
```

### 1.1. 实现提示
本文档给出的练习顺序只是建议，你也可能会发现其他实现顺序更适合自己。和之前一样，我们会通过检查你的代码并确认你是否通过了 `ant` 的 <tt>test</tt> 和 <tt>systemtest</tt> 目标来评分。完整评分说明及必须通过的测试见第 3.4 节。

下面是一种可行的实验路线。对应细节会在第 2 节展开。

*  实现 <tt>TableStats</tt> 类中的方法，使其能够用直方图（`IntHistogram` 类已提供骨架）或你自己设计的其他统计结构，来估计过滤条件的选择率以及扫描代价。
*  实现 <tt>JoinOptimizer</tt> 类中的方法，使其能够估计连接的代价与选择率。
*  编写 <tt>JoinOptimizer</tt> 中的 <tt>orderJoins</tt> 方法。该方法必须基于前两步计算出的统计信息，为一系列连接生成最优顺序（通常会用到 Selinger 算法）。

##  2. 优化器概览

回顾一下，基于代价的优化器的核心思想是：

*  利用表统计信息估计不同查询计划的“代价”。通常，计划代价与中间连接/选择结果的基数（即产生的元组数），以及过滤和连接谓词的选择率有关。
*  基于这些统计信息，以最优方式安排连接和选择的顺序，并从多种候选连接算法中选出最佳实现。

在本实验中，你将实现完成这两项工作的代码。

优化器会从 <tt>simpledb/Parser.java</tt> 中被调用。开始本实验前，你可能需要先回顾一下 <a href="https://github.com/MIT-DB-Class/simple-db-hw-2021/blob/master/lab2.md#27-query-parser">Lab 2 的解析器部分</a>。简而言之，如果你有一个描述表结构的 catalog 文件 <tt>catalog.txt</tt>，你可以通过如下方式启动解析器：
```
java -jar dist/simpledb.jar parser catalog.txt
```

当解析器启动时，它会先对所有表计算统计信息（使用你编写的统计代码）。当收到查询时，解析器会先把它转换为逻辑计划表示，再调用你的查询优化器生成一个最优的物理计划。

### 2.1 整体优化器结构
在开始实现之前，你需要理解 SimpleDB 优化器的整体结构。解析器与优化器模块的整体控制流程如图 1 所示。

<p align="center">
<img width=400 src="controlflow.png"><br>
<i>图 1：展示解析器中使用的类、方法和对象的流程图</i>
</p>


底部图例解释了图中使用的符号；你需要实现的是其中双边框标出的部分。后文会对这些类和方法进行更详细的说明（你可能需要反复参考该图）。整体流程如下：

1. <tt>Parser.java</tt> 在初始化时构造一组表统计信息（存放在 <tt>statsMap</tt> 容器中）。之后它等待用户输入查询，并对该查询调用 <tt>parseQuery</tt> 方法。
2.  <tt>parseQuery</tt> 首先构造一个表示已解析查询的 <tt>LogicalPlan</tt>。随后，它会对这个 `LogicalPlan` 实例调用 <tt>physicalPlan</tt> 方法。`physicalPlan` 返回一个 <tt>DBIterator</tt> 对象，可用于真正执行该查询。
    
在接下来的练习中，你将实现帮助 <tt>physicalPlan</tt> 生成最优计划所需的方法。

### 2.2. 统计估计
准确估计计划代价并不简单。在本实验中，我们只关注连接序列和基础表访问的代价，而不考虑访问方法选择（因为我们只有表扫描一种访问方式）以及其他算子（如聚合）的代价。

本实验只要求你考虑 left-deep 计划。关于可选加分功能，包括如何处理 bushy plan，可参考第 2.3 节。

####  2.2.1 整体计划代价

我们把连接计划写成 `p=t1 join t2 join ... tn` 的形式，表示一个 left-deep join，其中 `t1` 是最左侧（也即树中最深处）的连接。
给定一个计划 `p`，其代价可以写成：

```
scancost(t1) + scancost(t2) + joincost(t1 join t2) +
scancost(t3) + joincost((t1 join t2) join t3) +
... 
```

这里，`scancost(t1)` 表示扫描表 `t1` 的 I/O 代价，`joincost(t1,t2)` 表示将 `t1` 与 `t2` 做连接的 CPU 代价。为了让 I/O 和 CPU 的代价可比较，通常会引入一个常数比例因子，例如：

```
cost(predicate application) = 1
cost(pageScan) = SCALING_FACTOR x cost(predicate application)
```

在本实验中，你可以忽略缓存的影响（即认为每次访问一张表都要付出完整扫描代价）；同样，这部分你可以在第 2.3 节作为可选加分扩展实现。因此，`scancost(t1)` 只需视为 `t1` 的页数乘以 `SCALING_FACTOR`。

####  2.2.2 连接代价

在使用嵌套循环连接时，回忆一下：连接 `t1` 和 `t2`（其中 `t1` 是外表）的代价是：

```
joincost(t1 join t2) = scancost(t1) + ntups(t1) x scancost(t2) //IO cost
                       + ntups(t1) x ntups(t2)  //CPU cost
```

这里，`ntups(t1)` 是表 `t1` 中的元组数。

####  2.2.3 过滤选择率

对于基础表，`ntups` 可以通过扫描直接得到。但如果一个表上有一个或多个选择谓词，估计 `ntups` 就更麻烦了，这就是*过滤选择率估计*问题。下面是一种基于表值分布直方图的方法：

*  先扫描一遍表，计算表中每个属性的最小值与最大值。
*  为表中的每个属性构造一个直方图。一个简单做法是使用固定数量的桶 *NumB*，每个桶表示该属性值域中某个固定范围内的记录数。例如，若字段 *f* 的取值范围是 1 到 100，且有 10 个桶，那么第 1 个桶可以表示 1 到 10 之间的记录数，第 2 个桶表示 11 到 20 之间的记录数，以此类推。
*  再扫描一遍表，取出每个元组的各字段值，并据此填充各个直方图桶的计数。
*  对于等值表达式 *f=const* 的选择率估计，先找到包含 *const* 的桶。设该桶宽度（值域范围）为 *w*，高度（元组数）为 *h*，表总元组数为 *ntups*。在假设值在桶内均匀分布的情况下，该表达式的选择率近似为 *(h / w) / ntups*，因为 *(h/w)* 表示取值恰为 *const* 的预期元组数。
*  对于范围表达式 *f>const*，先找到 *const* 所在的桶 *b*，其宽度为 *w_b*，高度为 *h_b*。则桶 *b* 中元组占总元组的比例是 <nobr>*b_f = h_b / ntups* </nobr>。若假设值在桶 *b* 中均匀分布，则桶中大于 *const* 的部分比例为 <nobr>*(b_right - const) / w_b*</nobr>，其中 *b_right* 是该桶的右端点。因此，桶 *b* 对该谓词贡献的选择率为 *(b_f  x b_part)*。此外，桶 *b+1...NumB-1* 会完整贡献其各自的选择率（计算方式类似 *b_f*）。将这些贡献相加即可得到整体选择率。图 2 展示了这一过程。
*  对于“小于”表达式的选择率估计，可类似“大于”情况，从对应桶向左计算到桶 0。

<p align="center">
<img width=400 src="lab3-hist.png"><br>
<i>图 2：展示你将在 Lab 5 中实现的直方图</i>
</p>


接下来的两个练习中，你将实现过滤和连接的选择率估计。

***
**Exercise 1：`IntHistogram.java`**

你需要实现某种记录表统计信息的方法，用于选择率估计。我们提供了一个骨架类 <tt>IntHistogram</tt>。我们的本意是让你使用上面介绍的基于桶的直方图方法，但只要你的方法能给出合理的选择率估计，也可以自行采用其他实现。


我们还提供了一个 `StringHistogram` 类，它利用 `IntHistogram` 为字符串谓词计算选择率。如果你想实现更好的估计器，也可以修改 `StringHistogram`，不过完成本实验通常不需要改它。

完成这一练习后，你应当能够通过 <tt>IntHistogramTest</tt> 单元测试（如果你选择不使用基于直方图的选择率估计，则不要求通过这个测试）。

***
**Exercise 2：`TableStats.java`**

`TableStats` 类包含用于计算表的元组数、页数，以及估计该表字段上谓词选择率的方法。我们提供的查询解析器会为每张表创建一个 `TableStats` 实例，并将这些对象传递给你的查询优化器（后续练习会用到）。

你应当在 `TableStats` 中补全以下方法和逻辑：

*  实现 <tt>TableStats</tt> 构造函数：
   在你完成用于追踪统计信息（如直方图）的机制后，应在构造函数中加入扫描表（可能需要多次扫描）并构建统计信息的代码。
*   实现 <tt>estimateSelectivity(int field, Predicate.Op op,
    Field constant)</tt>：利用你的统计信息（例如依据字段类型使用 <tt>IntHistogram</tt> 或 <tt>StringHistogram</tt>），估计表上谓词 <tt>field op constant</tt> 的选择率。
*   实现 <tt>estimateScanCost()</tt>：该方法用于估计顺序扫描该文件的代价，已知每读取一页的代价是 <tt>costPerPageIO</tt>。你可以假设没有 seek，也没有页面已经驻留在 buffer pool 中。这个方法可以直接复用你在构造函数中预先计算的代价或大小信息。
*   实现 <tt>estimateTableCardinality(double
    selectivityFactor)</tt>：给定某个谓词的选择率 `selectivityFactor`，该方法返回应用该谓词后的关系元组数。它同样可以复用构造函数中计算的大小或代价信息。

你可能需要修改 `TableStats.java` 的构造函数，以便按上面介绍的方式对字段计算直方图，从而用于选择率估计。

完成这些任务后，你应当能够通过 <tt>TableStatsTest</tt> 中的单元测试。
***

#### 2.2.4 连接基数

最后，请注意：前面计划 `p` 的代价中包含诸如 <tt>joincost((t1 join t2) join
t3)</tt> 这样的表达式。要评估它，你需要能够估计 <tt>t1 join t2</tt> 的大小（<tt>ntups</tt>）。这就是*连接基数估计*问题，它通常比过滤选择率估计更难。本实验并不要求你做得特别复杂，不过第 2.4 节中的某个可选练习会介绍一种基于直方图的连接选择率估计方法。


在实现一个简单方案时，你应当牢记以下几点：

<!--  
  * <a name="change">The following three paragraphs are different in this version of the lab. </a> *
  .-->
*  对于等值连接，如果其中一个连接属性是主键，那么连接结果的元组数不会大于非主键一侧的基数。
* 对于没有主键参与的等值连接，很难准确判断输出大小。它可能接近两表基数乘积（如果两表中所有元组在该字段上值都相同），也可能是 0。你可以采用简单启发式（例如取两表中较大的那个基数）。
*  对于范围连接，同样很难精确估计结果大小。
   输出大小应当与输入大小成比例。你可以简单假设范围连接会输出笛卡尔积中的固定比例（例如 30%）。一般来说，在两张大小相同的表上，范围连接的代价应高于非主键等值连接。




***
**Exercise 3：连接代价估计**


`JoinOptimizer.java` 类中包含了连接排序与代价计算相关的全部方法。在本练习中，你将实现用于估计连接选择率和代价的方法，具体包括：

*  实现 <tt>
   estimateJoinCost(LogicalJoinNode j, int card1, int card2, double
   cost1, double cost2)</tt>：该方法估计连接 `j` 的代价，其中左输入基数为 `card1`，右输入基数为 `card2`，扫描左输入的代价为 `cost1`，访问右输入的代价为 `card2`。你可以假设连接采用嵌套循环，并套用前述公式。
*  实现 <tt>estimateJoinCardinality(LogicalJoinNode j, int
   card1, int card2, boolean t1pkey, boolean t2pkey)</tt>：该方法估计连接 `j` 的输出元组数，其中左输入大小为 `card1`，右输入大小为 `card2`，标志 `t1pkey` 和 `t2pkey` 分别表示左右连接字段是否唯一（即是否为主键）。

完成这些方法后，你应当能够通过 `JoinOptimizerTest.java` 中的单元测试 <tt>estimateJoinCostTest</tt> 和 <tt>estimateJoinCardinality</tt>。
***


###  2.3 连接顺序

现在你已经实现了代价估计方法，接下来将实现 Selinger 优化器。在这些方法中，连接被表示为一个 join node 列表（也就是跨两张表的谓词集合），而不是课堂里讲的待连接关系列表。

把课堂上的算法翻译成这种 join node 列表形式后，其伪代码大致如下：
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

为了帮助你实现该算法，我们提供了若干辅助类和方法。首先，`JoinOptimizer.java` 中的 `enumerateSubsets(List<T> v, int size)` 方法会返回 `v` 中所有大小为 `size` 的子集。对于大集合来说，这个方法效率很低；如果你实现一个更高效的枚举器，可以拿到额外加分（提示：考虑使用原地生成算法和惰性迭代器/流式接口，以避免显式构造整个幂集）。

其次，我们提供了以下方法：
```java
    private CostCard computeCostAndCardOfSubplan(Map<String, TableStats> stats, 
                                                Map<String, Double> filterSelectivities, 
                                                LogicalJoinNode joinToRemove,  
                                                Set<LogicalJoinNode> joinSet,
                                                double bestCostSoFar,
                                                PlanCache pc) 
```

给定一个连接子集（<tt>joinSet</tt>）以及一个要从中拿出来尝试的连接（<tt>joinToRemove</tt>），该方法会计算把 `joinToRemove` 加到 `joinSet - {joinToRemove}` 上的最佳方式。返回值是一个 `CostCard` 对象，包含该方案的代价、基数和最佳连接顺序（以列表表示）。如果找不到合法计划（例如不存在可行的 left-deep join），或者所有候选计划的代价都高于 `bestCostSoFar`，则 `computeCostAndCardOfSubplan` 可能返回 `null`。该方法内部会使用一个记录历史最优结果的缓存 `pc`（对应前面伪代码中的 `optjoin`），以便快速查找 `joinSet - {joinToRemove}` 的最优连接方式。其余参数 `stats` 和 `filterSelectivities` 将由你在 Exercise 4 中实现的 `orderJoins` 方法传入，后文会进一步解释。该方法本质上对应前面伪代码中的第 6 到第 8 行。

第三，我们还提供了以下方法：
```java
    private void printJoins(List<LogicalJoinNode> js, 
                           PlanCache pc,
                           Map<String, TableStats> stats,
                           Map<String, Double> selectivities)
```

当通过优化器的 `-explain` 选项打开 “explain” 标志时，该方法可用于图形化展示一个连接计划。

第四，我们还提供了 `PlanCache` 类，可在你实现 Selinger 算法时缓存目前为止某个连接子集的最优方案（使用 `computeCostAndCardOfSubplan` 时需要该类实例）。

***
**Exercise 4：连接排序**


在 `JoinOptimizer.java` 中实现以下方法：
```java
  List<LogicalJoinNode> orderJoins(Map<String, TableStats> stats, 
                   Map<String, Double> filterSelectivities,  
                   boolean explain)
```

这个方法应当基于 `joins` 成员变量工作，并返回一个新的 `List`，指定连接的执行顺序。返回列表中第 0 项表示 left-deep 计划中最左、最底层的那个连接。列表中相邻的连接应至少共享一个字段，以保证生成的是 left-deep 计划。这里 `stats` 提供了查询 `FROM` 列表中任意表名对应 `TableStats` 的能力；`filterSelectivities` 提供了查询某张表上谓词选择率的能力，保证 `FROM` 列表中的每个表名都对应一个条目。最后，`explain` 表示你是否需要输出连接顺序的可视化说明。


你可以使用上面介绍的辅助方法和类。总体上，你的实现应遵循前面的伪代码：枚举子集大小、枚举子集、枚举子计划，调用 `computeCostAndCardOfSubplan`，并构建一个 `PlanCache` 对象来记录每个子连接集合的最小代价执行方式。

完成该方法后，你应当能够通过 `JoinOptimizerTest` 中的所有单元测试，同时也应通过系统测试 `QueryTest`。

###  2.4 加分内容

本节介绍若干可选加分练习。它们不像前面的练习那样定义明确，但可以展示你对查询优化的掌握程度。请在报告中清楚标明你完成了哪些内容，并简要说明实现方式及结果（例如 benchmark 数据、经验总结等）。

***
**Bonus Exercises.** 每项 bonus 最多可获得 5% 额外加分：

*  *实现更高级的连接基数估计*。
   不要只使用简单启发式来估计连接基数，而是设计更复杂的算法。
    - 一种选择是为任意两张表 *t1* 和 *t2* 中任意两个属性 *a* 和 *b* 之间构造联合直方图。思路是：先对 *a* 做分桶；然后对 *a* 的每个桶 *A*，构造一个仅统计与 *A* 中 *a* 值共同出现的 *b* 值的直方图。
    - 另一种估计连接基数的方法是假设较小表中的每个值在较大表中都有匹配值。那么连接选择率可以写成：1/(*Max*(*num-distinct*(t1, column1), *num-distinct*(t2, column2)))。这里 `column1` 和 `column2` 是连接字段。连接基数则等于 `t1` 与 `t2` 基数乘积再乘以该选择率。<br>
*  *改进子集迭代器*。我们提供的 `enumerateSubsets` 实现效率较低，因为每次调用都会创建大量 Java 对象。在这个 bonus 中，你可以优化 `enumerateSubsets`，使系统可以在包含 20 个以上连接的计划上进行查询优化（当前实现对此类计划可能需要几分钟甚至几小时）。
*  *考虑缓存影响的代价模型*。当前扫描代价和连接代价估计没有考虑 buffer pool 缓存。你可以扩展代价模型以纳入缓存效应。这比较棘手，因为由于迭代器模型的存在，多个连接会同时运行，因此难以预测每个连接在之前实验里实现的简单 buffer pool 中究竟能获得多少内存。
*  *改进连接算法与算法选择*。我们当前的代价估计和连接算子选择方法（见 `JoinOptimizer.java` 中的 `instantiateJoin()`）只考虑嵌套循环连接。你可以扩展这些方法，使其支持一种或多种额外连接算法（例如基于内存 `HashMap` 的哈希连接）。
*  *Bushy 计划*。改进提供的 `orderJoins()` 及其他辅助方法，以支持生成 bushy join。我们的查询计划生成和可视化算法本身是能够处理 bushy 计划的；例如，如果 `orderJoins()` 返回列表 `(t1 join t2 ; t3 join t4 ; t2 join t3)`，这就对应一个以 `(t2 join t3)` 为根节点的 bushy 计划。

***


你现在已经完成本实验。
做得不错。

##  3. 提交流程
你必须提交代码（见下文），以及一份简短的 writeup（最多 2 页），说明你的实现思路。该 writeup 应包括：

*  描述你做出的设计决策，包括选择率估计方法、连接排序方法，以及你实现的任何加分项目及其实现方式（每个 bonus 最多可额外附加 1 页说明）。
*  讨论并说明你对 API 做出的任何修改。
*  描述你代码中缺失或未完成的部分。
*  描述你在本实验上花费的时间，以及你认为哪些地方特别困难或令人困惑。
*  描述你实现的任何额外加分内容。

###  3.1. 合作
本实验一个人即可完成，但如果你愿意，也可以与一位搭档合作。不允许更大的小组。请在你的 writeup 中清楚说明你与谁合作了（如果有）。

###  3.2. 提交作业
我们将使用 gradescope 对所有编程作业进行自动评分。你应该已经被邀请加入课程实例；如果没有，请联系我们，我们会帮你完成设置。你可以在截止日期前多次提交，我们将以 gradescope 记录的最后一次提交为准。请在提交中附带名为 `lab3-writeup.txt` 的 writeup 文件。你还需要显式加入自己创建的其他文件，例如新的 `*.java` 文件。

向 gradescope 提交最简单的方式是上传包含代码的 `.zip` 文件。在 Linux/MacOS 上，你可以运行：

```bash
$ zip -r submission.zip src/ lab3-writeup.txt
```

<a name="bugs"></a>
###  3.3. 报告 bug

SimpleDB 是一套相对复杂的代码。你很可能会遇到 bug、不一致之处、糟糕/过时/错误的文档等问题。

因此，我们希望你以一种探索性的心态完成本实验。如果某些东西不清楚，甚至是错误的，不要生气；请尽量自己先搞清楚，或者给我们发一封友好的邮件。

请将（友善的）bug 报告发送到 <a href="mailto:6.830-staff@mit.edu">6.830-staff@mit.edu</a>。
提交时请尽量包含：

* bug 的描述。
* 一个我们可以直接放入 `test/simpledb` 目录中编译并运行的 <tt>.java</tt> 文件。
* 一个能复现 bug 的 <tt>.txt</tt> 数据文件。我们应当能够使用 `HeapFileEncoder` 将其转换为 <tt>.dat</tt> 文件。

如果你觉得自己确实遇到了 bug，也可以在 Piazza 的课程页面发帖。


###  3.4 评分
<p>你总成绩的 75% 取决于你的代码是否能通过我们运行的系统测试套件。这些测试会是我们已提供测试的超集。在提交前，你应确保运行 <tt>ant test</tt> 和 <tt>ant systemtest</tt> 时都没有任何错误（即通过全部测试）。

**重要：** 在测试前，gradescope 会用我们自己的版本替换你的 <tt>build.xml</tt>、<tt>HeapFileEncoder.java</tt> 和整个 <tt>test</tt> 目录。这意味着你**不能**修改 <tt>.dat</tt> 文件格式。你在修改 API 时也要格外谨慎。你应当验证自己的代码能在未修改的测试上通过编译。

提交之后，gradescope 会立刻给出失败测试（如果有）的反馈和错误输出。自动评分部分的成绩将直接由该分数决定。额外 25% 的成绩将基于你的 writeup 质量以及我们对你代码的主观评价。这部分也会在我们完成评分后发布到 gradescope 上。

