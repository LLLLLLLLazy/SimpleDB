# 6.830/6.814 Lab 1：SimpleDB

**发布时间：2 月 24 日，周三**

**截止时间：3 月 10 日，周三，EDT 晚上 11:59**


<!--
**Bug 更新：** 我们有一个 [页面](bugs.html) 用来跟踪你或我们发现的
SimpleDB bug。针对 bug/烦人问题的修复也会发布在那里。
有些 bug 可能已经被发现，因此请查看该页面以获取实验代码的
最新版本 / 补丁。
-->

在 6.830 的实验作业中，你将编写一个基础的数据库管理系统，名为 SimpleDB。对于本次实验，你将重点实现访问磁盘上存储数据所需的核心模块；在后续实验中，你会继续加入各种查询处理算子，以及事务、锁和并发查询支持。

SimpleDB 使用 Java 编写。我们为你提供了一组大多尚未实现的类和接口。你需要为这些类补全代码。我们会使用 [JUnit](http://junit.sourceforge.net/) 编写的一组系统测试来评分。我们还提供了一些单元测试，这些测试不会直接用于评分，但对你验证代码正确性会很有帮助。我们也鼓励你在我们的测试之外自行编写测试套件。

本文档剩余部分将介绍 SimpleDB 的基本架构，给出一些开始编码的建议，并说明如何提交本实验。

我们**强烈建议**你尽早开始本实验。它需要你编写相当多的代码。

<!--

##  0.  找 bug、保持耐心、赢糖果

SimpleDB 是一套相对复杂的代码。
你很可能会遇到 bug、不一致之处，以及糟糕、
过时或不正确的文档等问题。

因此，我们希望你用一种探索性的心态完成本实验。
如果某些内容不清楚，甚至是错误的，不要生气；
请尝试自己搞清楚，或者给我们发一封友好的邮件。
随着 bug 和问题被报告，我们会通过发布修复、
更新 HW 仓库提交等方式提供帮助。

<p>……如果你在我们的代码中发现 bug，我们会送你一根糖果棒（见 [第 3.3 节](#bugs)）！

-->
<!--which you can find [here](bugs.html).</p>-->

## 0. 环境配置

**首先按照[这里](https://github.com/MIT-DB-Class/simple-db-hw-2021)的说明，从课程 GitHub 仓库下载 Lab 1 的代码。**

这些说明是为 Athena 或其他基于 Unix 的平台（例如 Linux、MacOS 等）编写的。由于代码使用 Java 编写，因此在 Windows 下理论上也可以工作，不过本文中的操作说明可能不完全适用。

我们还在[第 1.2 节](#eclipse)中加入了如何在 Eclipse 或 IntelliJ 中使用该项目的说明。

## 1. 开始

SimpleDB 使用 [Ant 构建工具](http://ant.apache.org/) 来编译代码并运行测试。Ant 类似于 [make](http://www.gnu.org/software/make/manual/)，但构建文件使用 XML 编写，在 Java 项目中更合适。大多数现代 Linux 发行版都自带 Ant。在 Athena 环境下，它位于 `sipb` locker 中，你可以在 Athena 提示符下输入 `add sipb` 来访问。注意，在某些 Athena 版本上，你还需要执行 `add -f java` 来正确配置 Java 程序的环境。更多细节可参考 [Athena 的 Java 使用文档](http://web.mit.edu/acs/www/languages.html#Java)。

为了帮助你开发，我们除了用于评分的端到端测试之外，还提供了一组单元测试。这些测试远称不上全面，因此你不应只依赖它们来验证项目正确性（请把你在 6.170 学到的本事也用上）。

运行单元测试请使用 `test` 构建目标：

```
$ cd [project-directory]
$ # 运行全部单元测试
$ ant test
$ # 运行指定单元测试
$ ant runtest -Dtest=TupleTest
```

你应该会看到类似下面的输出：

```
 build output...

test:
    [junit] Running simpledb.CatalogTest
    [junit] Testsuite: simpledb.CatalogTest
    [junit] Tests run: 2, Failures: 0, Errors: 2, Time elapsed: 0.037 sec
    [junit] Tests run: 2, Failures: 0, Errors: 2, Time elapsed: 0.037 sec

 ... stack traces and error reports ...
```

上面的输出表明编译/运行期间出现了两个错误；这是因为我们提供给你的初始代码本来就还不能工作。随着你完成实验各部分，你会逐步通过更多单元测试。

如果你在编码过程中想编写新的单元测试，应将它们放在 `test/simpledb` 目录下。

<p>关于 Ant 的更多使用方式，请参考其 [manual](http://ant.apache.org/manual/)。其中 [Running Ant](http://ant.apache.org/manual/running.html) 一节介绍了 `ant` 命令的详细用法。不过，下面这张速查表已经足够支撑你完成各次实验。

命令 | 说明
--- | ---
ant|构建默认目标（在 simpledb 中默认目标是 `dist`）。
ant -projecthelp|列出 `build.xml` 中所有可用目标及说明。
ant dist|编译 `src` 中的代码，并打包为 `dist/simpledb.jar`。
ant test|编译并运行全部单元测试。
ant runtest -Dtest=testname|运行名为 `testname` 的单元测试。
ant systemtest|编译并运行全部系统测试。
ant runsystest -Dtest=testname|运行名为 `testname` 的系统测试。

如果你使用 Windows，且不想从命令行运行 ant 测试，也可以在 Eclipse 中运行。右键点击 `build.xml`，在 targets 选项卡中可以看到 `runtest`、`runsystest` 等目标。例如，选择 `runtest` 就等价于命令行执行 `ant runtest`。类似 `-Dtest=testname` 这样的参数可以在 “Main” 选项卡的 “Arguments” 文本框中指定。你也可以复制 `build.xml`，修改目标和参数，并将其重命名为例如 `runtest_build.xml`，从而创建快捷方式。

### 1.1. 运行端到端测试

我们还提供了一组最终会用于评分的端到端测试。这些测试是 JUnit 测试，位于 `test/simpledb/systemtest` 目录下。运行全部系统测试，请使用 `systemtest` 构建目标：

```
$ ant systemtest

 ... build output ...

    [junit] Testcase: testSmall took 0.017 sec
    [junit]     Caused an ERROR
    [junit] expected to find the following tuples:
    [junit]     19128
    [junit] 
    [junit] java.lang.AssertionError: expected to find the following tuples:
    [junit]     19128
    [junit] 
    [junit]     at simpledb.systemtest.SystemTestUtil.matchTuples(SystemTestUtil.java:122)
    [junit]     at simpledb.systemtest.SystemTestUtil.matchTuples(SystemTestUtil.java:83)
    [junit]     at simpledb.systemtest.SystemTestUtil.matchTuples(SystemTestUtil.java:75)
    [junit]     at simpledb.systemtest.ScanTest.validateScan(ScanTest.java:30)
    [junit]     at simpledb.systemtest.ScanTest.testSmall(ScanTest.java:40)

 ... more error messages ...
```

<p>这表示该测试失败了，并给出了发现错误时的堆栈信息。调试时，首先去阅读报错位置对应的源代码。当测试通过时，你会看到类似下面的输出：

```
$ ant systemtest

 ... build output ...

    [junit] Testsuite: simpledb.systemtest.ScanTest
    [junit] Tests run: 3, Failures: 0, Errors: 0, Time elapsed: 7.278 sec
    [junit] Tests run: 3, Failures: 0, Errors: 0, Time elapsed: 7.278 sec
    [junit] 
    [junit] Testcase: testSmall took 0.937 sec
    [junit] Testcase: testLarge took 5.276 sec
    [junit] Testcase: testRandom took 1.049 sec

BUILD SUCCESSFUL
Total time: 52 seconds
```

#### 1.1.1 创建示例表

你很可能会想自己编写测试和数据表来验证 SimpleDB 的实现。你可以创建任意 `.txt` 文件，并用下面的命令将其转换为 SimpleDB `HeapFile` 格式的 `.dat` 文件：

```
$ java -jar dist/simpledb.jar convert file.txt N
```

其中 `file.txt` 是文件名，`N` 是文件的列数。注意，`file.txt` 必须满足如下格式：

```
int1,int2,...,intN
int1,int2,...,intN
int1,int2,...,intN
int1,int2,...,intN
```

……其中每个 `intN` 都是非负整数。

要查看一个表的内容，可使用 `print` 命令：

```
$ java -jar dist/simpledb.jar print file.dat N
```

其中 `file.dat` 是通过 `convert` 命令创建的表文件，`N` 是文件中的列数。

<a name="eclipse"></a>

### 1.2. 在 IDE 中使用

IDE（集成开发环境）是图形化的软件开发环境，有助于你管理更大的项目。我们提供了在 [Eclipse](http://www.eclipse.org) 和 [IntelliJ](https://www.jetbrains.com/idea/) 中配置项目的说明。Eclipse 的说明是在 Eclipse for Java Developers（不是 enterprise 版本）和 Java 1.7 环境下生成的。对于 IntelliJ，我们使用的是 Ultimate 版本，你可以通过 mit.edu 账号在[这里](https://www.jetbrains.com/community/education/#students)申请教育授权。我们强烈建议你为本项目配置并熟悉其中一个 IDE。

**准备代码库**

运行下面的命令，为 IDE 生成项目文件：

```
ant eclipse
```

**在 Eclipse 中配置实验**

* 安装好 Eclipse 后启动它。启动界面会要求你选择工作区路径（我们将该目录记作 $W）。请选择包含 simple-db-hw 仓库的目录。
* 在 Eclipse 中，选择 File->New->Project->Java->Java Project，然后点击 Next。
* 将项目名填写为 `simple-db-hw`。
* 在同一界面中选择 “Create project from existing source”，然后浏览到 `$W/simple-db-hw`。
* 点击 finish。你应当能在左侧 Project Explorer 中看到新的 `simple-db-hw` 项目。展开该项目后，你会看到上面提到的目录结构：实现代码在 `src` 中，单元测试和系统测试在 `test` 中。

**注意：** 本课程默认你使用 Oracle 官方发布版 Java。MacOS X 默认如此，大多数 Windows 上的 Eclipse 安装也如此；但很多 Linux 发行版默认使用其他 Java 运行时（如 OpenJDK）。请从 [Oracle 网站](http://www.oracle.com/technetwork/java/javase/downloads/index.html) 下载最新的 Java 8 更新并使用该版本。如果不切换，在后续实验中的某些性能测试里，你可能会看到莫名其妙的失败。

**运行单个单元测试和系统测试**

要运行单元测试或系统测试（它们都是 JUnit 测试，启动方式相同），请在左侧 Package Explorer 中展开 `simple-db-hw` 项目下的 `test` 目录。单元测试位于 `simpledb` 包中，系统测试位于 `simpledb.systemtests` 包中。要运行某个测试，选择测试文件（它们都叫 `*Test.java`，不要选 `TestUtil.java` 或 `SystemTestUtil.java`），右键选择 “Run As” -> “JUnit Test”。这样会弹出 JUnit 视图，显示测试套件中各个测试的状态，以及帮助你调试的异常和错误信息。

**运行 Ant 构建目标**

如果你想运行 `ant test` 或 `ant systemtest` 这样的命令，请在 Package Explorer 中右键点击 `build.xml`，选择 “Run As” -> “Ant Build...” （注意要选带省略号 `...` 的那个，否则你无法选择要运行的构建目标）。然后在下一界面的 “Targets” 标签页中勾选你想运行的目标（通常是 `dist` 以及 `test` 或 `systemtest` 之一）。执行后，结果会显示在 Eclipse 控制台窗口中。

**在 IntelliJ 中配置实验**

IntelliJ 是一款更现代的 Java IDE，很多人觉得它更直观。要使用 IntelliJ，请先安装并打开应用。与 Eclipse 类似，在 Projects 页面选择 Open，定位到你的项目根目录。双击 `.project` 文件（你可能需要让操作系统显示隐藏文件才能看到它），并选择 “open as project”。IntelliJ 提供了对 Ant 的工具窗口支持，你可以按[这里](https://www.jetbrains.com/help/idea/ant.html)的说明进行设置，但这不是开发所必需的。你还可以在[这里](https://www.jetbrains.com/help/idea/discover-intellij-idea.html)查看 IntelliJ 功能的详细介绍。

### 1.3. 实现提示

在开始写代码之前，我们**强烈建议**你通读整份文档，以便先建立对 SimpleDB 高层设计的整体理解。

<p>

你需要补全所有尚未实现的代码。我们认为你应该编写代码的位置通常都很明显。你可能需要增加私有方法和/或辅助类。你可以修改 API，但要确保我们的[评分](#grading)测试仍然可以运行，同时在 writeup 中说明、解释并论证你的修改。

<p>

除了本实验中需要补全的方法之外，类接口中还包含许多要在后续实验中才需要实现的方法。它们通常会以类级别说明：

```java
// Not necessary for lab1.
public class Insert implements DbIterator {
```

或者以方法级别说明：

```Java
public boolean deleteTuple(Tuple t)throws DbException{
        // some code goes here
        // not necessary for lab1
        return false;
        }
```

你提交的代码必须在**不修改这些方法**的前提下通过编译。

<p>

本文档给出了推荐的练习顺序，但你也可能会发现按照其他顺序实现更适合你。

**下面是一种可行的 SimpleDB 实现路线大纲：**

****

* 实现管理元组的类，即 `Tuple` 和 `TupleDesc`。我们已经为你实现了 `Field`、`IntField`、`StringField` 和 `Type`。由于你只需要支持整数、固定长度字符串以及固定长度元组，这部分比较直接。
* 实现 `Catalog`（这应该非常简单）。
* 实现 `BufferPool` 的构造函数和 `getPage()` 方法。
* 实现访问方法 `HeapPage`、`HeapFile` 以及相关的 ID 类。这些文件有相当一部分代码已经替你写好了。
* 实现算子 `SeqScan`。
* 到这里为止，你应该能通过 `ScanTest` 系统测试，这也是本实验的目标。

***

下面第 2 节会更详细地带你完成这些实现步骤，并说明每一步对应的单元测试。

### 1.4. 事务、锁和恢复

当你查看我们提供的接口时，会看到不少关于锁、事务和恢复的引用。本实验中你不需要支持这些功能，但你应该在代码接口中保留这些参数，因为你会在未来实验中实现事务和加锁。我们提供的测试代码会构造一个假的事务 ID，并把它传给查询涉及的各个算子；你应当将这个事务 ID 继续传递给其他算子和 buffer pool。

## 2. SimpleDB 架构与实现指南

SimpleDB 由以下部分组成：

* 表示字段、元组和元组模式的类；
* 将谓词和条件应用到元组上的类；
* 一种或多种访问方法（例如 heap file），它们将关系存储在磁盘上，并提供遍历这些关系中元组的方式；
* 一组处理元组的算子类（例如 select、join、insert、delete 等）；
* 一个在内存中缓存活跃元组和页面、并负责并发控制与事务的缓冲池（本实验中你无需关心后两者）；以及
* 一个保存可用表及其 schema 信息的 catalog。

SimpleDB 并不包含许多你可能认为“数据库应当具备”的东西。尤其是，SimpleDB **不包含**：

* （在本实验中）一个可以让你直接输入 SQL 查询的前端或解析器。相反，查询需要通过将一组算子手工串联成一个查询计划来构建（见[第 2.7 节](#query_walkthrough)）。后续实验中我们会提供一个简单解析器。
* 视图。
* 除整数和定长字符串之外的数据类型。
* （在本实验中）查询优化器。
* （在本实验中）索引。

<p>

在本节剩余部分中，我们会介绍你在本实验中需要实现的 SimpleDB 主要组件。你应当以这些练习为指导来完成实现。本文档并不是 SimpleDB 的完整规格说明；你仍然需要自己对系统多个部分的设计与实现做出决策。请注意，在 Lab 1 中，除了顺序扫描之外，你不需要实现其他算子（例如 select、join、project）。后续实验中你会逐步加入更多算子支持。

<p>

### 2.1. `Database` 类

`Database` 类提供对一组静态对象的访问，这些对象构成了数据库的全局状态。具体来说，它包括访问 catalog（数据库中所有表的列表）、buffer pool（当前驻留在内存中的数据库文件页集合）以及 log file 的方法。本实验中你不需要关心 log file。我们已经为你实现了 `Database` 类。你应该浏览一下这个文件，因为之后你需要通过它访问这些对象。

### 2.2. 字段与元组

<p>SimpleDB 中的元组非常基础。它们由一组 `Field` 对象构成，`Tuple` 中每个字段对应一个 `Field`。`Field` 是一个接口，不同的数据类型（如整数、字符串）会实现它。`Tuple` 对象由底层访问方法（例如 heap file 或 B-tree）创建，下一节会介绍。元组还带有一个类型（或模式），称为 _tuple descriptor_，由 `TupleDesc` 对象表示。这个对象包含一组 `Type` 对象，每个字段对应一个 `Type`，用来描述该字段的数据类型。

### Exercise 1

**在下面文件中实现骨架方法：**
***

* src/java/simpledb/storage/TupleDesc.java
* src/java/simpledb/storage/Tuple.java

***


此时，你的代码应当能够通过 `TupleTest` 和 `TupleDescTest` 单元测试。此时 `modifyRecordId()` 应当仍然失败，因为你还没有实现它。

### 2.3. `Catalog`

SimpleDB 中的 catalog（类 `Catalog`）由当前数据库中所有表及其 schema 的列表组成。你需要支持添加新表，以及获取指定表相关信息的能力。每张表都会关联一个 `TupleDesc` 对象，算子可通过它判断该表字段的类型和个数。

全局 catalog 是整个 SimpleDB 进程中唯一的一个 `Catalog` 实例。你可以通过 `Database.getCatalog()` 获取它；全局 buffer pool 同理，通过 `Database.getBufferPool()` 获取。

### Exercise 2

**在下面文件中实现骨架方法：**
***

* src/java/simpledb/common/Catalog.java

*** 

此时，你的代码应当可以通过 `CatalogTest` 中的单元测试。

### 2.4. `BufferPool`

<p>`BufferPool`（SimpleDB 中的 `BufferPool` 类）负责缓存最近从磁盘读取到内存中的页面。所有算子都通过 buffer pool 从磁盘上的各种文件读取和写入页面。它由固定数量的页面组成，这个数量由 `BufferPool` 构造函数中的 `numPages` 参数指定。在后续实验中，你将实现页面淘汰策略。本实验中，你只需要实现构造函数以及 `SeqScan` 算子所使用的 `BufferPool.getPage()` 方法。`BufferPool` 最多应缓存 `numPages` 个页面。对于本实验，如果对不同页面的请求数量超过了 `numPages`，你可以暂时不实现淘汰策略，而直接抛出 `DbException`。未来实验中你必须实现页面淘汰。

`Database` 类提供了静态方法 `Database.getBufferPool()`，它返回整个 SimpleDB 进程中的唯一 `BufferPool` 实例引用。

### Exercise 3

**在下面文件中实现 `getPage()` 方法：**

***

* src/java/simpledb/storage/BufferPool.java

***

我们没有为 `BufferPool` 单独提供单元测试。你实现的功能会在下面 `HeapFile` 的实现与测试中被间接验证。你应当使用 `DbFile.readPage` 方法来访问 `DbFile` 的页面。


<!--
When more than this many pages are in the buffer pool, one page should be
evicted from the pool before the next is loaded.  The choice of eviction
policy is up to you; it is not necessary to do something sophisticated.
-->

<!--
<p>

Notice that `BufferPool` asks you to implement
a `flush_all_pages()` method.  This is not something you would ever
need in a real implementation of a buffer pool.  However, we need this method
for testing purposes.  You really should never call this method from anywhere
in your code.
-->

### 2.5. `HeapFile` 访问方法

访问方法提供了一种读写按特定方式组织在磁盘上的数据的方式。常见访问方法包括 heap file（无序的元组文件）和 B-tree；在本次作业中，你只需要实现 heap file 访问方法，而我们已经为你写好了一部分代码。

<p>

`HeapFile` 对象由一组页面组成，每个页面都是固定字节大小，用于存储元组（大小由常量 `BufferPool.DEFAULT_PAGE_SIZE` 定义），并且还包含页面头信息。在 SimpleDB 中，每张表对应一个 `HeapFile` 对象。`HeapFile` 中的每个页面被组织为一组 slot，每个 slot 可容纳一个元组（同一张表中的元组大小相同）。除了这些 slot 之外，每个页面还有一个头部，它是一个位图：每个元组 slot 对应 1 bit。如果对应 bit 为 1，表示该元组有效；如果为 0，表示该元组无效（例如已被删除或从未初始化）。`HeapFile` 的页面类型为 `HeapPage`，它实现了 `Page` 接口。页面存储在 buffer pool 中，但由 `HeapFile` 负责读写。

<p>

SimpleDB 在磁盘上存储 heap file 的方式与其在内存中的布局大致相同。每个文件由连续排列的页面数据组成。每个页面由一个或多个字节表示 header，随后是 _page size_ 字节的实际页面内容。每个 tuple 需要 _tuple size_ * 8 bit 的内容空间，以及 1 bit 的 header 空间。因此，一个页面最多可容纳的元组数为：

<p>

`
_tuples per page_ = floor((_page size_ * 8) / (_tuple size_ * 8 + 1))
`

<p>

其中 _tuple size_ 是页面中单个元组的字节数。这里的思想是：每个元组除自身内容外，还需要额外 1 bit 在 header 中表示其状态。我们先计算页面总 bit 数（页面字节数乘以 8），再除以单个元组所需的 bit 数（包括这 1 个额外 header bit），得到每页可容纳的元组数。`floor` 表示向下取整到最近的整数个元组（因为我们不想在页面里存半个元组）。

<p>

在知道每页元组数之后，存储 header 所需的字节数为：
<p>

`
headerBytes = ceiling(tupsPerPage/8)
`

<p>

`ceiling` 表示向上取整到最近的整数个字节（因为我们不会只存储不到一个完整字节的头信息）。

<p>

每个字节的低位（最低有效位）表示文件中更靠前 slot 的状态。因此，第一个字节的最低位表示页面中第一个 slot 是否被使用；第一个字节的第二低位表示第二个 slot 是否被使用，以此类推。还要注意，最后一个字节的高位可能并不对应实际存在的 slot，因为 slot 数量未必是 8 的整数倍。另请注意，所有 Java 虚拟机都是 [big-endian](http://en.wikipedia.org/wiki/Endianness)。

<p>

### Exercise 4

**在下面文件中实现骨架方法：**
***

* src/java/simpledb/storage/HeapPageId.java
* src/java/simpledb/storage/RecordId.java
* src/java/simpledb/storage/HeapPage.java

***


尽管你不会在 Lab 1 中直接用到它们，我们仍要求你在 `HeapPage` 中实现 <tt>getNumEmptySlots()</tt> 和 <tt>isSlotUsed()</tt>。这些方法需要你操作页面 header 中的 bit。为理解页面布局，你可能会发现阅读 `HeapPage` 中已提供的其他方法，以及 <tt>src/simpledb/HeapFileEncoder.java</tt> 很有帮助。

你还需要实现一个遍历页面中元组的迭代器，这可能需要引入辅助类或数据结构。

此时，你的代码应当能通过 `HeapPageIdTest`、`RecordIDTest` 和 `HeapPageReadTest` 单元测试。


<p> 

在实现完 <tt>HeapPage</tt> 之后，你还需要在本实验中为 <tt>HeapFile</tt> 编写方法，用于计算文件中的页数以及从文件中读取页面。随后你就可以从磁盘上存储的文件中取出元组。

### Exercise 5

**在下面文件中实现骨架方法：**

***

* src/java/simpledb/storage/HeapFile.java

*** 

从磁盘读取页面时，你首先需要计算该页面在文件中的正确偏移量。提示：为了在任意偏移处读写页面，你需要对文件进行随机访问。从磁盘读取页面时，不应调用 `BufferPool` 的方法。

<p> 
你还需要实现 `HeapFile.iterator()` 方法，它应当遍历 `HeapFile` 中每个页面上的元组。这个迭代器必须通过 `BufferPool.getPage()` 访问 `HeapFile` 中的页面。该方法会将页面加载进 buffer pool，并将在后续实验中用于实现基于锁的并发控制和恢复。不要在 `open()` 调用时把整张表一次性全部加载到内存中，否则对非常大的表会导致内存溢出错误。

<p>

此时，你的代码应当能通过 `HeapFileReadTest` 单元测试。

### 2.6. 算子

算子负责真正执行查询计划。它们实现关系代数中的各种操作。在 SimpleDB 中，算子基于迭代器实现；每个算子都实现 `DbIterator` 接口。

<p>

算子通过把较底层的算子传入较高层算子的构造函数来组成计划，也就是把它们“串联”起来。位于计划叶子节点的特殊访问方法算子负责从磁盘读取数据（因此其下方没有其他算子）。

<p>

在计划的最顶层，与 SimpleDB 交互的程序只需对根算子调用 `getNext`；该算子随后调用子节点的 `getNext`，层层向下，直到调用到叶子算子。叶子算子从磁盘取回元组，并将其作为 `getNext` 的返回值向上传递；元组就这样在计划树中逐层传播，直到在根处输出，或被其他算子组合/过滤掉。

<p>

<!--
For plans that implement `INSERT` and `DELETE` queries,
the top-most operator is a special `Insert` or `Delete`
operator that modifies the pages on disk.  These operators return a tuple
containing the count of the number of affected tuples to the user-level
program.

<p>
-->

本实验中，你只需要实现一个 SimpleDB 算子。

### Exercise 6.

**在下面文件中实现骨架方法：**

***

* src/java/simpledb/execution/SeqScan.java

***
该算子会顺序扫描构造函数中 `tableid` 指定的表对应页面中的所有元组。它应通过 `DbFile.iterator()` 方法访问元组。

<p>此时，你应当可以完成 `ScanTest` 系统测试。做得不错。

其他算子将在后续实验中继续实现。

<a name="query_walkthrough"></a>

### 2.7. 一个简单查询

本节用于说明这些组件是如何协同工作来处理一个简单查询的。

假设你有一个数据文件 `some_data_file.txt`，内容如下：

```
1,1,1
2,2,2 
3,4,4
```

<p>
你可以用下面的方式把它转换为一个可供 SimpleDB 查询的二进制文件：
<p>
```java -jar dist/simpledb.jar convert some_data_file.txt 3```
<p>
这里参数 `3` 表示输入有 3 列。
<p>
下面的代码在这个文件上实现了一个简单的选择查询。这段代码等价于 SQL 语句 `SELECT * FROM some_data_file`。

```
package simpledb;
import java.io.*;

public class test {

    public static void main(String[] argv) {

        // construct a 3-column table schema
        Type types[] = new Type[]{ Type.INT_TYPE, Type.INT_TYPE, Type.INT_TYPE };
        String names[] = new String[]{ "field0", "field1", "field2" };
        TupleDesc descriptor = new TupleDesc(types, names);

        // create the table, associate it with some_data_file.dat
        // and tell the catalog about the schema of this table.
        HeapFile table1 = new HeapFile(new File("some_data_file.dat"), descriptor);
        Database.getCatalog().addTable(table1, "test");

        // construct the query: we use a simple SeqScan, which spoonfeeds
        // tuples via its iterator.
        TransactionId tid = new TransactionId();
        SeqScan f = new SeqScan(tid, table1.getId());

        try {
            // and run it
            f.open();
            while (f.hasNext()) {
                Tuple tup = f.next();
                System.out.println(tup);
            }
            f.close();
            Database.getBufferPool().transactionComplete(tid);
        } catch (Exception e) {
            System.out.println ("Exception : " + e);
        }
    }

}
```

我们创建的表有三个整数字段。为了表达这一点，我们创建了一个 `TupleDesc` 对象，并传入一个 `Type` 数组以及可选的 `String` 字段名数组。创建好 `TupleDesc` 后，我们初始化一个 `HeapFile` 对象，表示存储在 `some_data_file.dat` 中的表。再之后，我们把该表加入 catalog。如果这是一个已经运行着的数据库服务器，那么这些 catalog 信息通常已经存在；这里只是为了让示例代码自包含，所以需要显式加载。

完成数据库系统初始化后，我们构建查询计划。这个计划只包含一个 `SeqScan` 算子，它负责从磁盘扫描元组。一般来说，这类算子在实例化时会拿到对应表（如 `SeqScan`）或子算子（如 `Filter`）的引用。测试程序随后不断调用 `SeqScan` 的 `hasNext` 和 `next`。当 `SeqScan` 输出元组时，这些元组会被打印到命令行。

我们**强烈建议**你把它作为一个有趣的端到端测试亲手跑一遍，这会帮助你积累为 SimpleDB 编写测试程序的经验。你应当在 `src/java/simpledb` 目录下创建文件 `test.java`，内容即上面的代码，并在代码前补上需要的 `import` 语句，同时将 `some_data_file.dat` 文件放在项目顶层目录。然后运行：

```
ant
java -classpath dist/simpledb.jar simpledb.test
```

注意，`ant` 会编译 `test.java`，并生成一个包含它的新 jar 文件。

## 3. 提交流程

你必须提交代码（见下文），以及一份简短的 writeup（最多 2 页）说明你的实现思路。该 writeup 应包括：

* 描述你做出的设计决策。对于 Lab 1，这部分可能很少。
* 讨论并说明你对 API 做出的任何修改。
* 描述你代码中缺失或未完成的部分。
* 描述你在本实验上花费的时间，以及你认为哪些地方特别困难或令人困惑。

### 3.1. 合作

本实验一个人就可以完成，但如果你愿意，也可以和一位搭档一起做。不允许更大的小组。请在你的个人 writeup 中清楚说明你与谁合作了（如果有）。

### 3.2. 提交作业

<!--
To submit your code, please create a <tt>6.830-lab1.tar.gz</tt> tarball (such
that, untarred, it creates a <tt>6.830-lab1/src/simpledb</tt> directory with
your code) and submit it on the [6.830 Stellar Site](https://stellar.mit.edu/S/course/6/sp13/6.830/index.html). You can use the `ant handin` target to generate the tarball.
-->

我们将使用 gradescope 对所有编程作业进行自动评分。你应该已经被邀请加入课程实例；如果没有，请查看 piazza 上的邀请码。如果仍有问题，请联系我们，我们会帮助你完成设置。在截止日期前你可以提交多次，我们将以 gradescope 记录的**最后一次**提交为准。请在提交中附带名为 `lab1-writeup.txt` 的 writeup 文件。
你还需要显式加入你创建的其他文件，例如新的 `*.java` 文件。

向 gradescope 提交最简单的方式是提交包含代码的 `.zip` 文件。在 Linux/MacOS 上，你可以运行如下命令：

```bash
$ zip -r submission.zip src/ lab1-writeup.txt
```

### 3.3. 报告 bug

请将（友善的）bug 报告发送到 [6.830-staff@mit.edu](mailto:6.830-staff@mit.edu)。提交 bug 时，请尽量包含：

* bug 的描述。
* 一个我们可以直接放进 `test/simpledb` 目录中编译并运行的 `.java` 文件。
* 一个能复现该 bug 的 `.txt` 数据文件。我们应当能够使用 `HeapFileEncoder` 将其转换为 `.dat` 文件。

如果你是第一个报告某个特定 bug 的人，我们会送你一根糖果棒。

<!--The latest bug reports/fixes can be found [here](bugs.html).-->

<a name="grading"></a>

### 3.4 评分

<p>你总成绩的 75% 将取决于你的代码能否通过我们运行的系统测试套件。这些测试会是我们已提供测试的超集。在提交前，你应确保运行 <tt>ant test</tt> 和 <tt>ant systemtest</tt> 时都没有任何错误（即通过所有测试）。

**重要：** 在测试之前，gradescope 会用我们自己的版本替换你的 <tt>build.xml</tt> 和整个 <tt>test</tt> 目录。这意味着你**不能**修改 <tt>.dat</tt> 文件格式。你在修改 API 时也要非常谨慎。你应当验证自己的代码能够在未修改的测试上通过编译。

提交后，你会立即从 gradescope 获得失败测试（如果有）的反馈和错误输出。给出的分数将作为自动评分部分的成绩。另有 25% 的成绩将基于 writeup 质量以及我们对你代码的主观评价。这部分成绩会在我们完成评分后同样发布到 gradescope 上。

我们在设计这次作业时投入了很多心思，也希望你能享受动手实现的过程。
