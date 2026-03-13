# 6.830 Lab 5：B+ Tree Index

**发布时间：2021 年 4 月 21 日，周三**<br>
**截止时间：2021 年 5 月 4 日，周二，EDT 晚上 11:59**

## 0. 简介


在本实验中，你将实现一个 B+ 树索引，用于高效查找与范围扫描。我们已经为你提供了实现树结构所需的所有底层代码。你将实现搜索、页面分裂、页面间元组重分配以及页面合并。

你可能会发现，回顾教材第 10.3--10.7 节会很有帮助，这几节详细介绍了 B+ 树的结构，以及搜索、插入和删除的伪代码。

正如教材和课堂中所描述的，B+ 树的内部节点包含多个 entry，每个 entry 由一个 key 值以及左右两个子节点指针组成。相邻 key 会共享一个子指针，因此一个包含 *m* 个 key 的内部节点会有 *m*+1 个子指针。叶子节点可以直接保存数据项，也可以保存指向其他数据库文件中数据项的指针。为简化起见，我们这里实现的 B+ 树会让叶子页直接保存数据项。相邻叶子页通过左右兄弟指针连接，因此范围扫描只需要先通过根节点和内部节点做一次搜索找到起始叶子页，之后就可以沿着右（或左）兄弟指针继续遍历相邻叶子页。

## 1. 开始

你应当从自己提交的 Lab 4 代码开始（如果你没有提交 Lab 4，或者你的解答不能正常工作，请联系助教讨论可选方案）。此外，本实验还提供了一些原始代码分发包中没有的新源码和测试文件。

你需要把这些新文件加入到当前代码中，并设置好 `lab4` 分支。最简单的方法是进入项目目录（通常名为 `simple-db-hw`），设置分支并从主 GitHub 仓库拉取：

``` $ cd simple-db-hw $ git pull upstream master ```


## 2. 搜索

请先看看 `index/` 和 `BTreeFile.java`。这是 B+ 树实现的核心文件，你在本实验中的主要代码都会写在这里。与 `HeapFile` 不同，`BTreeFile` 由四类页面组成。你可能已经猜到，其中有两类页面对应树的节点：内部页和叶子页。内部页由 `BTreeInternalPage.java` 实现，叶子页由 `BTreeLeafPage.java` 实现。为了方便，我们还提供了一个抽象类 `BTreePage.java`，包含叶子页和内部页的公共逻辑。此外，头页由 `BTreeHeaderPage.java` 实现，用于跟踪文件中哪些页面正在使用。最后，每个 `BTreeFile` 开头还有一个单独的页面，指向根页以及第一个头页，这个单例页面由 `BTreeRootPtrPage.java` 实现。请先熟悉这些类的接口，尤其是 `BTreePage`、`BTreeInternalPage` 和 `BTreeLeafPage`，因为你后续实现 B+ 树时会频繁使用它们。


你的第一项任务是在 `BTreeFile.java` 中实现 `findLeafPage()` 函数。该函数用于在给定某个 key 值时找到相应的叶子页，同时会被搜索和插入两个场景共同使用。例如，假设我们有一棵只有两个叶子页的 B+ 树（见图 1）。根节点是一个内部页，其中只有一个 entry，包含一个 key（此例中是 6）和两个子指针。如果给定值 1，该函数应返回第一个叶子页；如果给定值 8，则应返回第二个叶子页。比较不直观的是当给定 key 值为 6 时的情况。因为 key 可能重复，所以两个叶子页中都可能有 6。在这种情况下，函数应返回第一个（左侧）叶子页。

<p align="center"> <img width=500 src="simple_tree.png"><br> <i>图 1：一棵包含重复 key 的简单 B+ 树</i> </p>


你的 `findLeafPage()` 应当递归地沿内部节点向下搜索，直到到达与所给 key 值对应的叶子页为止。为了在每一步找到合适的子页面，你应当遍历内部页中的 entries，并将每个 entry 的 key 与给定 key 进行比较。`BTreeInternalPage.iterator()` 会通过 `BTreeEntry.java` 中定义的接口让你访问内部页中的 entries。借助这个迭代器，你可以遍历内部页中的 key，并读取每个 key 对应的左右子页 id。递归的终止条件是传入的 `BTreePageId` 的 `pgcateg()` 等于 `BTreePageId.LEAF`，表示它是一个叶子页。此时你只需从 buffer pool 中取出该页并返回，不需要额外确认该页里是否真的包含给定 key 值 `f`。


你的 `findLeafPage()` 代码还必须处理 `f` 为 `null` 的情况。当 `f` 为 `null` 时，你应当在每一层都递归地走向最左子节点，以找到最左侧叶子页。找到最左叶子页对于扫描整个文件非常有用。正如前面所说，你可以通过 `BTreePageId.java` 中的 `pgcateg()` 函数检查页面类型。你可以假设传入该函数的页面只可能是叶子页或内部页。


对于获取内部页和叶子页，不建议你直接调用 `BufferPool.getPage()`，而是建议使用我们提供的包装函数 `BTreeFile.getPage()`。它与 `BufferPool.getPage()` 的行为完全一致，只是额外接收一个参数，用于跟踪脏页列表。这个函数会在接下来两道练习中非常重要，因为那时你会真正更新数据，需要跟踪哪些页面被修改了。


在你的 `findLeafPage()` 实现中，访问到的每一个内部（非叶子）页都应以 READ_ONLY 权限获取；只有最终返回的叶子页，才应使用函数参数中传入的权限获取。这个权限区别在本实验中可能暂时无关紧要，但对于代码在后续实验中继续正确工作非常重要。

***

**Exercise 1：`BTreeFile.findLeafPage()`**


实现 `BTreeFile.findLeafPage()`。


完成该练习后，你应当能够通过 `BTreeFileReadTest.java` 中的全部单元测试，以及 `BTreeScanTest.java` 中的系统测试。

***


## 3. 插入

为了让 B+ 树中的元组保持有序，并维持树结构完整性，我们必须把元组插入到包含该 key 范围的叶子页中。正如前面所说，`findLeafPage()` 可以用来找到应插入该元组的正确叶子页。然而，每个页面的 slot 数量是有限的，因此即使对应叶子页已经满了，我们仍然要能继续插入元组。


按照教材所述，向一个已满的叶子页插入元组时，应将该页分裂成两个新页，并使元组尽可能平均地分布在这两个页面中。每当叶子页分裂时，都需要在父节点中增加一个新的 entry，对应第二个页面中的第一条元组。父内部节点有时也可能满了，无法再容纳新的 entry；此时父节点自身也需要分裂，并把一个新的 entry 再插入到它的父节点中。这种分裂可能递归向上传播，最终甚至会产生一个新的根节点。


在这个练习中，你需要在 `BTreeFile.java` 中实现 `splitLeafPage()` 和 `splitInternalPage()`。如果被分裂的页面本身就是根页，那么你需要创建一个新的内部节点作为新的根，并更新 `BTreeRootPtrPage`。否则，你需要以 READ_WRITE 权限获取父页面；如果父页面也已满，则递归地分裂它，然后再向其中加入新 entry。函数 `getParentWithEmptySlots()` 会非常适合处理这些不同情况。注意，在 `splitLeafPage()` 中，你应当把 key “复制（copy）”到父页面；而在 `splitInternalPage()` 中，你应当把 key “上推（push）”到父页面。如果对此感到困惑，请参考图 2 以及教材第 10.5 节。别忘了按需要更新新页面的 parent 指针（为简洁起见，图中没有画 parent 指针）。当内部节点分裂时，你还需要更新所有被移动子节点的 parent 指针。你可能会发现 `updateParentPointers()` 很适合做这项工作。此外，别忘了更新被分裂叶子页的兄弟指针。最后，根据提供的 key 字段，返回应该插入新元组或新 entry 的页面。（提示：你不必关心提供的 key 恰好落在分裂中心这一事实。分裂时可以完全忽略该 key，仅在最后决定返回左右哪一页时再用它。）

<p align="center"> <img width=500 src="splitting_leaf.png"><br> <img width=500
src="splitting_internal.png"><br> <i>图 2：页面分裂</i> </p>


每当你创建新页面时，无论是因为页面分裂还是因为创建新根，都应调用 `getEmptyPage()` 来获得该页。这个抽象会让我们在后面介绍的“合并页面后复用已删除页面”的场景中得以重用空间。

我们期望你通过 `BTreeLeafPage.iterator()` 和 `BTreeInternalPage.iterator()` 来遍历叶子页和内部页中的元组/entry。为了方便，我们也提供了两类页面的反向迭代器：`BTreeLeafPage.reverseIterator()` 和 `BTreeInternalPage.reverseIterator()`。当你需要将部分元组/entry 移动到右兄弟页时，这些反向迭代器尤其有用。

正如前面提到的，内部页迭代器使用 `BTreeEntry.java` 中定义的接口，其中一个 entry 包含一个 key 和两个子指针，还带有一个 `recordId`，用于标识该 key 和这些子指针在底层页面中的位置。我们认为“按 entry 为单位”操作内部页是比较自然的方式，但必须牢记，底层页面实际上并不是存储一个 entry 列表，而是存储有序的 *m* 个 key 和 *m*+1 个子指针列表。由于 `BTreeEntry` 只是一个接口，并不是实际存储在页面上的对象，因此直接修改 `BTreeEntry` 的字段并不会更新底层页面。若要修改页面上的数据，你需要调用 `BTreeInternalPage.updateEntry()`。此外，删除一个 entry 实际上只会删除一个 key 和一个子指针，因此我们提供了 `BTreeInternalPage.deleteKeyAndLeftChild()` 与 `BTreeInternalPage.deleteKeyAndRightChild()` 两个函数，使这一点更明确。entry 中的 `recordId` 被用来定位要删除的 key 和子指针。插入 entry 时，同样也只是插入一个 key 和一个子指针（除非它是第一个 entry），因此 `BTreeInternalPage.insertEntry()` 会检查：你给出的 entry 中必须有一个子指针与页面上已有子指针重合，并且把该 entry 插入该位置后，页面中的 key 顺序仍然保持有序。

在 `splitLeafPage()` 和 `splitInternalPage()` 中，你都需要把所有新创建页面、以及因新指针或新数据而修改过的页面加入 `dirtypages` 集合。这正是 `BTreeFile.getPage()` 会派上用场的地方。每次你取页面时，`BTreeFile.getPage()` 都会先查看该页面是否已经在本地缓存 `dirtypages` 中；如果没有，才会去 buffer pool 中取。若页面是以读写权限取出的，`BTreeFile.getPage()` 也会把它加入 `dirtypages`，因为你大概率马上会把它弄脏。这种做法的一个好处是：在单次插入或删除元组过程中，如果同一页面被多次访问，可以避免更新丢失。

请注意，与 `HeapFile.insertTuple()` 有很大不同，`BTreeFile.insertTuple()` 可能会返回一大批 dirty page，尤其是在内部页发生分裂时。你可能还记得，在前几次实验中返回这组 dirty page 的目的是防止 buffer pool 在这些脏页刷盘之前把它们淘汰掉。

***

**警告**：由于 B+ 树是一个相当复杂的数据结构，在修改它之前，最好先明确“合法 B+ 树”必须满足的性质。下面给出一个非正式清单：

1. 如果父节点指向某个子节点，那么该子节点必须反向指回同一个父节点。
2. 如果某个叶子节点指向一个右兄弟，那么这个右兄弟必须反向指回该叶子节点作为左兄弟。
3. 第一片叶子和最后一片叶子的左/右兄弟指针应分别为 null。
3. `RecordId` 必须与它真正所在的页面一致。
4. 对于拥有非叶子子节点的节点来说，其中的 `key` 必须大于左子树中的任意 key，并且小于右子树中的任意 key。
5. 对于拥有叶子子节点的节点来说，其中的 `key` 必须大于等于左子页中的任意 key，并且小于等于右子页中的任意 key。
6. 一个节点的所有子节点必须要么全是非叶子，要么全是叶子。
7. 非根节点不能低于半满状态。

我们已经在 `BTreeChecker.java` 中实现了对这些性质的自动检查。`systemtest/BTreeFileDeleteTest.java` 也会使用该检查器来测试你的 B+ 树实现。你可以像我们在 `BTreeFileDeleteTest.java` 中那样，主动插入对这个检查函数的调用来辅助调试。

**注意：**
1. 在树初始化完成后，以及一次完整的 key 插入或删除调用开始前和结束后，检查器应当始终通过；但在某些内部辅助方法执行过程中，不要求始终通过。

2. 一棵树可能在结构上“合法”（即能通过 `checkRep()`），但语义上依然错误。例如，一棵空树永远能通过 `checkRep()`，但它并不一定是正确的；如果你刚刚插入了一个元组，那么树显然不应还是空的。 ***

**Exercise 2：页面分裂**


实现 `BTreeFile.splitLeafPage()` 和 `BTreeFile.splitInternalPage()`。

完成该练习后，你应当能够通过 `BTreeFileInsertTest.java` 中的单元测试，以及 `systemtest/BTreeFileInsertTest.java` 中的系统测试。某些系统测试可能需要几秒钟才能完成。这些测试会验证你的代码是否能正确插入元组、正确分裂页面，并正确处理重复元组。

<!-- After completing this exercise, you should be able to pass the unit tests
in `BTreeDeadlockTest.java` and `BTreeInsertTest.java`. Some of the test cases
may take a few seconds to complete. `BTreeDeadlockTest` will test that you have
implemented locking correctly and can handle deadlocks. `BTreeInsertTest` will
test that your code inserts tuples and splits pages correcty, and also handles
duplicate tuples and next key locking. -->

***


## 4. 删除

为了保持树的平衡并避免浪费空间，B+ 树中的删除可能会导致页面之间重新分配元组（见图 3），或者最终发生页面合并（见图 4）。你可能需要复习教材第 10.6 节。

<p align="center"> <img width=500 src="redist_leaf.png"><br> <img width=500
src="redist_internal.png"><br> <i>图 3：页面重分配</i> </p>

<p align="center"> <img width=500 src="merging_leaf.png"><br> <img width=500
src="merging_internal.png"><br> <i>图 4：页面合并</i> </p>

如教材所述，当尝试从一个低于半满的叶子页中删除元组时，应当触发以下两者之一：要么从某个兄弟页“借”元组，要么与某个兄弟页合并。如果某个兄弟页还有多余元组，那么应在两个页面之间尽量均匀地重新分配元组，并相应地更新父节点中的 entry（见图 3）。但如果兄弟页也已经处于最小占用状态，那么就应将这两个页面合并，并从父节点中删除相应 entry（见图 4）。反过来，从父节点中删除 entry 又可能使父节点本身低于半满；此时父节点同样需要从兄弟页借 entry 或与兄弟页合并。这可能递归地向上触发合并，甚至在根节点最后一个 entry 被删除时导致根节点消失。

在本练习中，你需要在 `BTreeFile.java` 中实现 `stealFromLeafPage()`、`stealFromLeftInternalPage()`、`stealFromRightInternalPage()`、`mergeLeafPages()` 和 `mergeInternalPages()`。前 3 个函数需要在兄弟页仍有富余元组/entry 时，把元组/entry 重新均匀分配。别忘了同步更新父节点中对应的 key 字段（仔细看图 3 中这一步是怎么做的，key 实际上是通过父节点“旋转”过去的）。在 `stealFromLeftInternalPage()` / `stealFromRightInternalPage()` 中，你还需要更新被移动子节点的 parent 指针。你应当能够复用 `updateParentPointers()` 来完成这件事。

在 `mergeLeafPages()` 和 `mergeInternalPages()` 中，你需要实现页面合并逻辑，也就是 `splitLeafPage()` 和 `splitInternalPage()` 的逆过程。函数 `deleteParentEntry()` 会非常适合用来处理这些递归场景。别忘了对已删除页面调用 `setEmptyPage()`，使其可被重用。和前几道题一样，我们建议你使用 `BTreeFile.getPage()` 来统一封装“获取页面并维护 dirty page 列表”的逻辑。

***

**Exercise 3：页面重分配**

实现 `BTreeFile.stealFromLeafPage()`、
`BTreeFile.stealFromLeftInternalPage()`、
`BTreeFile.stealFromRightInternalPage()`。

完成该练习后，你应当能够通过 `BTreeFileDeleteTest.java` 中的部分单元测试（例如 `testStealFromLeftLeafPage` 和 `testStealFromRightLeafPage`）。由于系统测试会构建较大的 B+ 树来充分验证系统，因此它们可能需要几秒钟才能跑完。


**Exercise 4：页面合并**

实现 `BTreeFile.mergeLeafPages()` 和 `BTreeFile.mergeInternalPages()`。

到这里，你应当能够通过 `BTreeFileDeleteTest.java` 中的全部单元测试，以及 `systemtest/BTreeFileDeleteTest.java` 中的系统测试。

***


## 5. 事务

你可能记得，B+ 树可以通过 next-key locking 防止两次连续范围扫描之间出现 phantom tuple。由于 SimpleDB 使用页面级、严格两阶段锁，因此如果 B+ 树实现正确，phantom 防护实际上会“免费”得到。也就是说，此时你还应当能够通过 `BTreeNextKeyLockingTest`。

此外，如果你在 B+ 树代码内部正确实现了加锁，你还应当能够通过 `test/simpledb/BTreeDeadlockTest.java` 中的测试。

如果一切都实现正确，你还应当能通过 `BTreeTest` 系统测试。我们预计很多人会觉得 `BTreeTest` 很难，因此它不是必做项；但如果你能成功跑过，我们会给额外加分。请注意，这个测试最多可能需要约一分钟。

## 6. 额外加分

***

**Bonus Exercise 5：（10% 额外加分）**

创建并实现一个名为 `BTreeReverseScan` 的类，它可以在给定可选 `IndexPredicate` 的情况下反向扫描 `BTreeFile`。

你可以从 `BTreeScan` 作为起点，但你大概率还需要在 `BTreeFile` 中实现一个反向迭代器，也可能需要单独实现一个适用于反向扫描版本的 `BTreeFile.findLeafPage()`。我们已经在 `BTreeLeafPage` 和 `BTreeInternalPage` 中提供了反向迭代器，你很可能会用得上。你还应自己编写测试，验证实现是否正确。`BTreeScanTest.java` 是一个不错的参考。

***


## 7. 提交流程

你必须提交代码（见下文），以及一份简短的 writeup（最多 1 页），说明你的实现思路。该 writeup 应包括：

*  描述你做出的设计决策，包括任何困难之处或意料之外的地方。

*  讨论并说明你在 `BTreeFile.java` 之外做出的任何修改。

*  这次实验花了你多长时间？你对如何改进它有什么建议？

*  可选：如果你完成了额外加分练习，请说明你的实现，并展示你做了充分测试。


###  7.1. 合作
本实验一个人即可完成，但如果你愿意，也可以和一位搭档合作。不允许更大的小组。请在你的 writeup 中清楚说明你与谁合作了（如果有）。

###  7.2. 提交作业
我们将使用 gradescope 对所有编程作业进行自动评分。你应该已经被邀请加入课程实例；如果没有，请联系我们，我们会帮助你完成设置。你可以在截止日期前多次提交，我们将以 gradescope 记录的最后一次提交为准。请在提交中附带名为 `lab3-writeup.txt` 的 writeup 文件。你还需要显式加入自己创建的其他文件，例如新的 `*.java` 文件。

向 gradescope 提交最简单的方式是上传包含代码的 `.zip` 文件。在 Linux/MacOS 上，你可以运行：

```bash
$ zip -r submission.zip src/ lab5-writeup.txt
```

<a name="bugs"></a>
###  7.3. 报告 bug

SimpleDB 是一套相对复杂的代码。你很可能会遇到 bug、不一致之处、糟糕/过时/错误的文档等问题。

因此，我们希望你以探索性的心态完成本实验。如果某些东西不清楚，甚至是错误的，不要生气；请尽量自己先搞清楚，或者给我们发一封友好的邮件。

请将（友善的）bug 报告发送到 <a href="mailto:6.830-staff@mit.edu">6.830-staff@mit.edu</a>。
提交时请尽量包含：

* bug 的描述。
* 一个我们可以直接放入 `test/simpledb` 目录中编译并运行的 <tt>.java</tt> 文件。
* 一个能复现 bug 的 <tt>.txt</tt> 数据文件。我们应当能够使用 `HeapFileEncoder` 将其转换为 <tt>.dat</tt> 文件。

如果你觉得自己遇到了 bug，也可以在 Piazza 的课程页面发帖。


###  7.4 评分
<p>你总成绩的 75% 将取决于你的代码能否通过我们运行的系统测试套件。这些测试会是我们已提供测试的超集。在提交前，你应确保运行 <tt>ant test</tt> 和 <tt>ant systemtest</tt> 时都没有任何错误（即通过全部测试）。

**重要：** 在测试前，gradescope 会用我们自己的版本替换你的 <tt>build.xml</tt>、<tt>HeapFileEncoder.java</tt> 和整个 <tt>test</tt> 目录。这意味着你**不能**修改 <tt>.dat</tt> 文件格式。你在修改 API 时也要格外谨慎。你应当验证自己的代码能在未修改的测试上通过编译。

提交后，gradescope 会立刻给出失败测试（如果有）的反馈和错误输出。自动评分部分的成绩将由此决定。额外 25% 的成绩将基于你的 writeup 质量以及我们对代码的主观评价。这部分也会在我们完成评分后发布到 gradescope 上。

我们在设计这次实验时投入了很多心思，也希望你能享受实现它的过程。
