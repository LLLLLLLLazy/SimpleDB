# 6.830 Lab 2：SimpleDB Operators

**发布时间：2021 年 3 月 9 日，周二**<br>
**截止时间：2021 年 3 月 19 日，周五，EDT 晚上 11:59**


<!--
Version History:


3/1/12 : Initial version
-->



在本次实验中，你将为 SimpleDB 编写一组算子，以支持表修改（例如插入和删除记录）、选择、连接以及聚合。这些内容会建立在你在 Lab 1 中完成的基础之上，使数据库系统能够对多张表执行简单查询。

此外，在 Lab 1 中我们忽略了 buffer pool 管理问题：也就是数据库运行期间引用的页面数可能超过内存可容纳数量。在 Lab 2 中，你将设计一种页面淘汰策略，将旧页面从 buffer pool 中刷出。

本实验中你不需要实现事务或加锁。

本文档剩余部分会给出一些开始编码的建议，描述一组帮助你逐步完成实验的练习，并说明如何提交代码。本实验需要编写不少代码，因此我们鼓励你**尽早开始**。

<a name="starting"></a>

## 1. 开始

你应当从自己提交的 Lab 1 代码开始（如果你没有提交 Lab 1，或者你的解答不能正常工作，请联系助教讨论可选方案）。此外，本实验还额外提供了一些在原始代码分发包中没有的新源码和测试文件。

### 1.1. 获取 Lab 2

你需要将这些新文件加入到当前代码中。最简单的方法是进入项目目录（通常名为 `simple-db-hw`），然后从主 GitHub 仓库拉取：

```
$ cd simple-db-hw
$ git pull upstream master
```

**IDE 用户**还需要更新项目依赖，把新的 jar 包包含进来。最简单的方法是再次运行：
```
ant eclipse
```

然后重新用 Eclipse 或 IntelliJ 打开项目。

如果你对项目配置做过其他修改，不想丢失它们，也可以手动添加依赖。对于 Eclipse，在 package explorer 中右键项目名（通常是 <tt>simple-db-hw</tt>），选择 **Properties**。在左侧选择 **Java Build Path**，再在右侧选择 **Libraries** 标签页。点击 **Add JARs...**，选择 **zql.jar** 和 **jline-0.9.94.jar**，然后点击 **OK**，再点击 **OK**。这样你的代码就应当可以编译了。对于 IntelliJ，进入 **File** -> **Project Structure**，在 **Modules** 下选择 <tt>simpledb</tt> 项目，切换到 **Dependencies** 标签页。在面板底部点击 <tt>+</tt> 图标，将这些 jar 加为编译期依赖。


### 1.2. 实现提示

和之前一样，我们**强烈建议**你在动手写代码前先通读整份文档，先理解 SimpleDB 的高层设计。

本文档中的练习顺序只是建议，你也可能发现其他实现顺序更适合自己。和之前一样，我们会通过检查你的代码并确认你是否通过了 `ant` 的 `test` 和 `systemtest` 目标来评分。注意，本实验中代码只需要通过文档明确指出的那些测试，而不是所有单元测试和系统测试。完整评分说明及需要通过的测试列表见第 3.4 节。

下面是一种可行的实现路线；对应细节和练习会在第 2 节中展开：

* 实现 `Filter` 和 `Join` 算子，并确认对应测试可以通过。这些算子的 Javadoc 详细说明了其行为。我们已经为你实现了 `Project` 和 `OrderBy`，它们可以帮助你理解其他算子的工作方式。

* 实现 `IntegerAggregator` 和 `StringAggregator`。在这里，你需要写出在一串输入元组中，针对某一字段并按组进行聚合的真实计算逻辑。计算平均值时请使用整数除法，因为 SimpleDB 只支持整数。`StringAggregator` 只需要支持 `COUNT` 聚合，因为其他操作对字符串没有意义。

* 实现 `Aggregate` 算子。与其他算子一样，聚合算子也实现 `OpIterator` 接口，因此它可以被放入 SimpleDB 查询计划中。注意，`Aggregate` 的输出是每组一个聚合结果，`next()` 每次返回一条聚合后的结果；构造函数会接收聚合字段和分组字段。

* 实现 `BufferPool` 中与元组插入、删除和页面淘汰相关的方法。此时你还不需要考虑事务。

* 实现 `Insert` 和 `Delete` 算子。和所有算子一样，`Insert` 和 `Delete` 也实现 `OpIterator`：它们接收一个待插入/删除的元组流，输出一个只含单个整数字段的元组，表示插入或删除了多少条记录。这两个算子需要调用 `BufferPool` 中真正修改磁盘页面的方法。确认插入和删除相关测试正确通过。

请注意，SimpleDB 不实现任何一致性或完整性检查，因此可以把重复记录插入文件，也没有办法强制执行主键或外键约束。

到此为止，你应当能够通过 `systemtest` 目标中的测试，这就是本实验的目标。

你也将能够使用我们提供的 SQL 解析器对数据库运行 SQL 查询。简要教程见[第 2.7 节](#parser)。

最后，你可能注意到本实验中的一些迭代器继承的是 `Operator` 类，而不是直接实现 `OpIterator` 接口。因为 `next` / `hasNext` 的实现往往重复、烦琐且容易出错，`Operator` 将这部分逻辑做了通用实现，只要求你实现更简单的 `readNext`。你可以采用这种风格，也可以继续直接实现 `OpIterator`。如果你想实现 `OpIterator` 接口，只需把迭代器类中的 `extends Operator` 改为 `implements OpIterator`。

## 2. SimpleDB 架构与实现指南

### 2.1. `Filter` 和 `Join`

回顾一下，SimpleDB 的 `OpIterator` 类实现了关系代数中的操作。现在你将实现两个算子，使你可以执行比单表扫描更有趣一些的查询。

* *Filter*：该算子只返回满足构造函数中指定 `Predicate` 的元组，因此会过滤掉所有不匹配的元组。

* *Join*：该算子根据构造函数中传入的 `JoinPredicate` 将左右两个子算子的元组连接起来。我们只要求你实现简单的嵌套循环连接，但你也可以尝试更复杂的连接实现，并在实验 writeup 中说明你的实现。

**Exercise 1.**

在下列文件中实现骨架方法：

***  

* src/java/simpledb/execution/Predicate.java
* src/java/simpledb/execution/JoinPredicate.java
* src/java/simpledb/execution/Filter.java
* src/java/simpledb/execution/Join.java

***  

此时，你的代码应当能通过 `PredicateTest`、`JoinPredicateTest`、`FilterTest` 和 `JoinTest` 单元测试。同时，你还应能通过系统测试 `FilterTest` 和 `JoinTest`。

### 2.2. 聚合

SimpleDB 的另一个算子支持带 `GROUP BY` 子句的基础 SQL 聚合。你需要实现五种 SQL 聚合：`COUNT`、`SUM`、`AVG`、`MIN`、`MAX`，并支持分组。你只需要支持对单个字段进行聚合，以及按单个字段分组。

为了计算聚合，我们使用 `Aggregator` 接口，它会把新元组合并到现有聚合结果中。`Aggregator` 在构造时会被告知采用哪种聚合操作。之后，客户端代码应当对 child 迭代器中的每个元组调用 `Aggregator.mergeTupleIntoGroup()`。在所有元组都被合并后，客户端可获取一个包含聚合结果的 `OpIterator`。结果中的每个元组格式为 `(groupValue, aggregateValue)`；如果分组字段取值为 `Aggregator.NO_GROUPING`，则结果是单个 `(aggregateValue)` 形式的元组。

注意，这种实现所需空间与不同分组的数量成线性关系。对于本实验，你无需担心分组数量超过可用内存的情况。

**Exercise 2.**

在下列文件中实现骨架方法：

***  

* src/java/simpledb/execution/IntegerAggregator.java
* src/java/simpledb/execution/StringAggregator.java
* src/java/simpledb/execution/Aggregate.java

***  

此时，你的代码应当能通过 `IntegerAggregatorTest`、`StringAggregatorTest` 和 `AggregateTest` 单元测试。同时，你还应能通过系统测试 `AggregateTest`。

### 2.3. `HeapFile` 的可变性

现在我们开始实现支持修改表的方法。首先从单个页面和文件层面入手。这里主要有两类操作：添加元组和移除元组。

**删除元组：** 要删除元组，你需要实现 `deleteTuple`。元组中包含 `RecordID`，因此你可以定位到该元组所在页面，这样删除操作本质上就是找到对应页面并正确修改页面头部信息。

**添加元组：** `HeapFile.java` 中的 `insertTuple` 方法负责向 heap file 中添加元组。要向 `HeapFile` 中插入新元组，你需要找到一个存在空 slot 的页面。如果 `HeapFile` 中不存在这样的页面，你需要创建一个新页面并将其追加到磁盘文件末尾。你还需要确保元组中的 `RecordID` 被正确更新。

**Exercise 3.**

在下列文件中实现剩余的骨架方法：

***  

* src/java/simpledb/storage/HeapPage.java
* src/java/simpledb/storage/HeapFile.java<br>
  （注意：此时你未必必须实现 `writePage`。）

***



要实现 `HeapPage`，你需要在诸如 <tt>insertTuple()</tt> 和 <tt>deleteTuple()</tt> 这样的函数中修改头部位图。你会发现 Lab 1 中要求实现的 <tt>getNumEmptySlots()</tt> 和 <tt>isSlotUsed()</tt> 是很有用的抽象。注意，我们已经提供了 <tt>markSlotUsed</tt> 方法，作为修改页面头部某个 slot 是否占用的抽象接口。

请注意，`HeapFile.insertTuple()` 和 `HeapFile.deleteTuple()` 这两个方法必须通过 <tt>BufferPool.getPage()</tt> 获取页面；否则，你在下一个实验中的事务实现将无法正确工作。

你还需要在 <tt>src/simpledb/BufferPool.java</tt> 中实现以下骨架方法：

***  

* insertTuple()
* deleteTuple()

***  


这些方法应调用被修改表对应 `HeapFile` 中的相应方法（引入这一层间接调用是为了将来支持其他类型的文件，例如索引）。

此时，你的代码应当能通过 `HeapPageWriteTest`、`HeapFileWriteTest` 以及 `BufferPoolWriteTest` 单元测试。

### 2.4. 插入与删除

现在你已经写好了 `HeapFile` 层面对元组进行添加和删除的所有机制，接下来需要实现 `Insert` 和 `Delete` 算子。

对于执行 `insert` 和 `delete` 查询的计划，最顶层算子是特殊的 `Insert` 或 `Delete` 算子，它会修改磁盘上的页面。这些算子返回受影响元组数量，具体实现方式是返回一个只包含单个整数字段的元组。

* *Insert*：该算子会把从子算子读到的元组插入到构造函数指定的 `tableid` 对应的表中。它应使用 `BufferPool.insertTuple()` 完成插入。

* *Delete*：该算子会把从子算子读到的元组从构造函数指定的 `tableid` 对应表中删除。它应使用 `BufferPool.deleteTuple()` 完成删除。

**Exercise 4.**

在下列文件中实现骨架方法：

***  

* src/java/simpledb/execution/Insert.java
* src/java/simpledb/execution/Delete.java

***  

此时，你的代码应当能通过 `InsertTest` 单元测试。我们没有为 `Delete` 提供单元测试。同时，你还应能通过系统测试 `InsertTest` 和 `DeleteTest`。

### 2.5. 页面淘汰

在 Lab 1 中，我们并没有真正遵守 `BufferPool` 构造函数参数 `numPages` 所定义的 buffer pool 最大页面数量限制。现在，你需要选择一种页面淘汰策略，并修改之前读取或创建页面的代码以实现该策略。

当 buffer pool 中页面数超过 <tt>numPages</tt> 时，在加载新页面之前必须先淘汰一个页面。具体采用什么淘汰策略由你决定，不必实现得太复杂。请在实验 writeup 中说明你的策略。

注意，`BufferPool` 要求你实现 `flushAllPages()` 方法。真实的 buffer pool 实现通常并不需要它；我们只是为了测试方便才要求实现。你不应在任何真实运行逻辑中调用该方法。

由于我们实现 `ScanTest.cacheTest` 的方式，你需要保证 `flushPage` 和 `flushAllPages` 这两个方法**不会**从 buffer pool 中淘汰页面，否则无法通过该测试。

`flushAllPages` 应调用 `flushPage` 处理 `BufferPool` 中所有页面；`flushPage` 应将任何 dirty page 写回磁盘并将其标记为非 dirty，但页面仍应保留在 `BufferPool` 中。

唯一应当从 buffer pool 中移除页面的方法是 `evictPage`；如果它淘汰的是 dirty page，那么必须先调用 `flushPage`。

**Exercise 5.**

在下列文件中补全 `flushPage()` 及其他辅助方法，以实现页面淘汰：

***  

* src/java/simpledb/storage/BufferPool.java

***



如果你之前没有在
<tt>HeapFile.java</tt> 中实现 `writePage()`，那么这里也需要补上。最后，你还应实现 `discardPage()`，用于**不写回磁盘**地将页面从 buffer pool 中移除。本实验不会测试 `discardPage()`，但它对后续实验是必须的。

此时，你的代码应当能通过系统测试 `EvictionTest`。

由于我们不会要求某种特定的淘汰策略，这个测试的方式是：创建一个仅有 16 页的 `BufferPool`（注意：虽然 `DEFAULT_PAGES` 是 50，但这里初始化的是更小值），然后扫描一个页面数量远多于 16 的文件，检查 JVM 内存使用量是否增加超过 5 MB。如果你没有正确实现淘汰策略，就无法淘汰足够多的页面，最终会超出限制，从而测试失败。

你现在已经完成本实验。做得不错。

<a name="query_walkthrough"></a>

### 2.6. 查询示例

下面的代码实现了一个简单的连接查询，作用于两张表，这两张表都由三列整数组成。（`some_data_file1.dat` 和 `some_data_file2.dat` 是这些文件页面的二进制表示。）这段代码等价于下面的 SQL 语句：

```sql
SELECT *
FROM some_data_file1,
     some_data_file2
WHERE some_data_file1.field1 = some_data_file2.field1
  AND some_data_file1.id > 1
```

如果你想看更丰富的查询操作示例，可以去阅读连接、过滤和聚合相关的单元测试。

```java
package simpledb;

import java.io.*;

public class jointest {

    public static void main(String[] argv) {
        // construct a 3-column table schema
        Type types[] = new Type[]{Type.INT_TYPE, Type.INT_TYPE, Type.INT_TYPE};
        String names[] = new String[]{"field0", "field1", "field2"};

        TupleDesc td = new TupleDesc(types, names);

        // create the tables, associate them with the data files
        // and tell the catalog about the schema  the tables.
        HeapFile table1 = new HeapFile(new File("some_data_file1.dat"), td);
        Database.getCatalog().addTable(table1, "t1");

        HeapFile table2 = new HeapFile(new File("some_data_file2.dat"), td);
        Database.getCatalog().addTable(table2, "t2");

        // construct the query: we use two SeqScans, which spoonfeed
        // tuples via iterators into join
        TransactionId tid = new TransactionId();

        SeqScan ss1 = new SeqScan(tid, table1.getId(), "t1");
        SeqScan ss2 = new SeqScan(tid, table2.getId(), "t2");

        // create a filter for the where condition
        Filter sf1 = new Filter(
                new Predicate(0,
                        Predicate.Op.GREATER_THAN, new IntField(1)), ss1);

        JoinPredicate p = new JoinPredicate(1, Predicate.Op.EQUALS, 1);
        Join j = new Join(p, sf1, ss2);

        // and run it
        try {
            j.open();
            while (j.hasNext()) {
                Tuple tup = j.next();
                System.out.println(tup);
            }
            j.close();
            Database.getBufferPool().transactionComplete(tid);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
```

两张表都包含三个整数字段。为了表示这一点，我们创建一个 `TupleDesc` 对象，并向其传入一个表示字段类型的 `Type` 数组以及一个表示字段名的 `String` 数组。创建好 `TupleDesc` 后，我们初始化两个 `HeapFile` 对象来表示这两张表。接着把它们加入 `Catalog`。（如果这是一个已在运行中的数据库服务器，这些 catalog 信息应当早已存在；这里仅为了测试而显式加载。）

完成数据库初始化后，我们构建查询计划。这个计划包含两个 `SeqScan` 算子，分别扫描两个文件中的元组；第一个 `HeapFile` 的扫描后接一个 `Filter` 算子；最后接一个 `Join` 算子，根据 `JoinPredicate` 对两张表的元组做连接。一般来说，这些算子在创建时会接收对应表（如 `SeqScan`）或子算子（如 `Join`）的引用。测试程序会反复调用 `Join` 的 `next`，而 `Join` 又会从自己的子算子中拉取元组。`Join` 输出的元组会被打印到命令行。

<a name="parser"></a>

### 2.7. 查询解析器

我们为 SimpleDB 提供了一个查询解析器，在你完成本实验中的练习后，就可以用它编写和运行 SQL 查询。

第一步是创建一些数据表和 catalog。假设你有一个 `data.txt` 文件，内容如下：

```
1,10
2,20
3,30
4,40
5,50
5,50
```

你可以通过 `convert` 命令把它转换成一个 SimpleDB 表（记得先执行 <tt>ant</tt>）：

```
java -jar dist/simpledb.jar convert data.txt 2 "int,int"
```

这会生成一个 `data.dat` 文件。除了表的原始数据之外，额外两个参数表示每条记录有两个字段，类型分别是 `int` 和 `int`。

接着，创建一个 catalog 文件 `catalog.txt`，内容如下：

```
data (f1 int, f2 int)
```

这告诉 SimpleDB：存在一张表 `data`（存储在 `data.dat` 中），它有两个整数字段，字段名分别是 `f1` 和 `f2`。

最后，启动解析器。你必须从命令行运行 java（`ant` 不能很好地处理交互式目标）。
在 `simpledb/` 目录下输入：

```
java -jar dist/simpledb.jar parser catalog.txt
```

你应当会看到类似这样的输出：

```
Added table : data with schema INT(f1), INT(f2), 
SimpleDB> 
```

现在你就可以执行查询了：

```
SimpleDB> select d.f1, d.f2 from data d;
Started a new transaction tid = 1221852405823
 ADDING TABLE d(data) TO tableMap
     TABLE HAS  tupleDesc INT(d.f1), INT(d.f2), 
1       10
2       20
3       30
4       40
5       50
5       50

 6 rows.
----------------
0.16 seconds

SimpleDB> 
```

这个解析器功能相对完整（包括支持 `SELECT`、`INSERT`、`DELETE` 和事务），但也存在一些问题，错误信息不一定总是足够清晰。以下是一些需要注意的限制：

* 每个字段名都必须带上表名作为前缀，即使该字段名本身没有歧义也不行（你可以使用表别名，如上例所示，但不能使用 `AS` 关键字）。

* 支持在 `WHERE` 子句中写嵌套查询，但不支持在 `FROM` 子句中写嵌套查询。

* 不支持算术表达式（例如不能对两个字段求和）。

* 最多只允许一个 `GROUP BY` 和一个聚合列。

* 不允许使用集合型操作符，例如 `IN`、`UNION` 和 `EXCEPT`。

* `WHERE` 子句中只允许 `AND` 表达式。

* 不支持 `UPDATE` 表达式。

* 允许字符串操作符 `LIKE`，但必须完整写出，不能使用 Postgres 风格的波浪号 `[~]` 简写。

## 3. 提交流程

你必须提交代码（见下文），以及一份简短的 writeup（最多 2 页）说明你的实现思路。该 writeup 应包括：

* 描述你做出的设计决策，包括你选择的页面淘汰策略。如果你使用的不是嵌套循环连接，请说明你选择的算法及其权衡。

* 讨论并说明你对 API 做出的任何修改。

* 描述你代码中缺失或未完成的部分。

* 描述你在本实验上花费的时间，以及你认为哪些地方特别困难或令人困惑。

### 3.1. 合作

本实验一个人可以完成，但如果你愿意，也可以与一位搭档合作。不允许更大的小组。请在你的个人 writeup 中清楚说明你与谁合作了（如果有）。

### 3.2. 提交作业

我们将使用 gradescope 对所有编程作业进行自动评分。你应该已经被邀请加入课程实例；如果没有，请查看 piazza 上的邀请码。如果你仍然无法加入，请告诉我们，我们会帮助你完成设置。你可以在截止日期前多次提交，我们将以 gradescope 记录的最后一次提交为准。请在提交中附带名为 `lab2-writeup.txt` 的 writeup 文件。你还需要显式加入自己创建的其他文件，例如新的 `*.java` 文件。

向 gradescope 提交最简单的方式是上传包含代码的 `.zip` 文件。在 Linux/MacOS 上，你可以运行：

```bash
$ zip -r submission.zip src/ lab2-writeup.txt
```

<a name="bugs"></a>

### 3.3. 报告 bug

SimpleDB 是一套相对复杂的代码。你很可能会遇到 bug、不一致之处、糟糕/过时/错误的文档等问题。

因此，我们希望你以一种探索性的心态来完成本实验。如果某些东西不清楚，甚至是错误的，不要生气；请尝试自己搞清楚，或者给我们发一封友好的邮件。

请将（友善的）bug 报告发送到 [6.830-staff@mit.edu](mailto:6.830-staff@mit.edu)。提交时请尽量包含：

* bug 的描述。

* 一个我们可以直接放到 `test/simpledb` 目录中编译并运行的 <tt>.java</tt> 文件。

* 一个可复现 bug 的 <tt>.txt</tt> 数据文件。我们应能够使用 `HeapFileEncoder` 将其转换成 <tt>.dat</tt> 文件。

如果你觉得自己遇到了 bug，也可以在 Piazza 的课程页面发帖。

<a name="grading"></a>

### 3.4 评分

<p>你总成绩的 75% 取决于你的代码是否能通过我们运行的系统测试套件。这些测试会是我们已提供测试的超集。在提交前，你应确保运行 <tt>ant test</tt> 和 <tt>ant systemtest</tt> 时都没有错误（即通过所有测试）。

**重要：** 在测试之前，gradescope 会用我们自己的版本替换你的 <tt>build.xml</tt>、<tt>HeapFileEncoder.java</tt> 以及整个 <tt>test</tt> 目录。这意味着你**不能**修改 <tt>.dat</tt> 文件格式。你在修改 API 时也要谨慎。你应当验证自己的代码能在未修改的测试上通过编译。

提交之后，gradescope 会立刻给出失败测试（如果有）的反馈和错误输出。该分数将作为自动评分部分的成绩。额外 25% 的成绩将基于你的 writeup 质量以及我们对代码的主观评价。这部分也会在我们完成评分后发布到 gradescope 上。

我们在设计这次实验时投入了很多心思，也希望你能享受实现的过程。
