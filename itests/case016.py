import warnings
warnings.simplefilter(action='ignore', category=FutureWarning)
warnings.simplefilter(action='ignore')

import pandas as pd

import numpy as np
import glob
import subprocess
import shutil
from os import mkdir
from os.path import basename, isdir
from scipy.stats import linregress
from scipy.special import binom
import itertools


############################################################################################################
###################################### UTILS ###############################################################
############################################################################################################

def create_folder(downsampling):
    if not isdir(f'individual_{downsampling}_test'):
        mkdir(f'individual_{downsampling}_test')


def read_tables(downsampling):
    table_list = []
    for files in glob.glob(f'downsampled_{downsampling}/*.tsv'):
        name = basename(files).split('.')[0]
        table = pd.read_table(files, sep='\t')
        table['sampleId'] = name
        table_list.append(table)
    final_table = pd.concat(table_list)
    return final_table


def test_table_format(table):
    table.index = table.index + '.clns'
    table.reset_index(inplace=True)
    table.rename(columns={ table.columns[0]: "sampleId" }, inplace = True)
    table.sort_values(list(table.columns[:3]), inplace=True)
    table = table[sorted(table.columns)]
    table.columns.name = None
    return table


def vj_usage(table, downsampling):
    table['v'] = table['allVHitsWithScore'].apply(lambda x: x.split('*')[0]+'*00')
    table['j'] = table['allJHitsWithScore'].apply(lambda x: x.split('*')[0]+'*00')
    table = table[['readCount', 'readFraction', 'v', 'j', 'sampleId']]
    v_usage = table.groupby(['v', 'sampleId'], as_index=False)['readFraction'].sum()
    v_usage_pivoted = v_usage.pivot_table(index='sampleId', columns='v', values='readFraction')
    j_usage = table.groupby(['j', 'sampleId'], as_index=False)['readFraction'].sum()
    j_usage_pivoted = j_usage.pivot_table(index='sampleId', columns='j', values='readFraction')
    table_vj = table.groupby(['sampleId', 'v', 'j'], as_index=False)['readFraction'].sum()
    table_vj.set_index('sampleId', inplace=True)
    table_vj.columns = ['VGene', 'JGene', 'Value']
    v_table = test_table_format(v_usage_pivoted)
    j_table = test_table_format(j_usage_pivoted)
    vj_table = test_table_format(table_vj)
    v_table.to_csv(f'individual_{downsampling}_test/test.vUsage.tsv', sep='\t', index=False)
    j_table.to_csv(f'individual_{downsampling}_test/test.jUsage.tsv', sep='\t', index=False)
    vj_table.to_csv(f'individual_{downsampling}_test/test.vjUsage.tsv', sep='\t', index=False)


def extractCdr3AAcenter(cdr3aa, cdr3aaLen):
    if cdr3aaLen < 5:
        return cdr3aa
    elif cdr3aaLen % 2 == 0:
        pos_from = int((cdr3aaLen / 2) - 3)
        pos_to = int((cdr3aaLen / 2) + 2)
    else:
        pos_from = int(((cdr3aaLen - 1) / 2) - 2)
        pos_to = int(((cdr3aaLen + 1) / 2) + 2)
    return cdr3aa[pos_from:pos_to]


def calcProp(cdr3center, prop):
    prop_sum = 0
    for aa in cdr3center:
        prop_sum += prop[aa]
    return prop_sum


def aaprops_dict(path_to_aaprops):
    aaprops = pd.read_table(path_to_aaprops, sep=',')
    colsN2 = [col for col in aaprops.columns if col.startswith('n2')] + ['charge']
    aaprops.set_index('aa', inplace=True)
    aapropsN2 = aaprops[colsN2]
    aapropsN2_dict = aapropsN2.to_dict()
    return aapropsN2_dict


def addedNucleotides(row):
    if row['allDHitsWithScore'] is np.nan:
        return row['ntLengthOfVJJunction']
    else:
        vd = int(row['refPoints'].split(':')[12]) - int(row['refPoints'].split(':')[11])
        dj = int(row['refPoints'].split(':')[16]) - int(row['refPoints'].split(':')[15])
        return (vd + dj) * row['readFraction']


def biophysics(table, downsampling):
    table = table[table['allVHitsWithScore'].str.contains('TRBV')]
    table['cdr3aa_len'] = table['aaSeqCDR3'].str.len()
    table['aaLengthOfCDR3'] = table['cdr3aa_len'] * table['readFraction']
    table['ntLengthOfCDR3'] = table['nSeqCDR3'].str.len() * table['readFraction']
    table['ntLengthOfVJJunction'] = np.where(table['nSeqVJJunction'].isna(), 0, table['nSeqVJJunction'].str.len()) * table['readFraction']
    table['cdr3-center-5'] = table.apply(lambda x: extractCdr3AAcenter(x["aaSeqCDR3"], x["cdr3aa_len"]), axis=1)
    aapropsN2_dict = aaprops_dict('aaprops.csv')
    for props, prop in aapropsN2_dict.items():
        table[props] = np.vectorize(calcProp)(table['cdr3-center-5'], prop) * table['readFraction']
    table['AddedNucleotides'] = table.apply(addedNucleotides, axis=1)
    table = table[['readFraction', 'readCount', 'sampleId', 'ntLengthOfCDR3', 'aaLengthOfCDR3', 'ntLengthOfVJJunction', 'AddedNucleotides', 'n2strength', 'n2hydrophobicity', 'n2volume', 'n2surface', 'charge']]
    table.columns = ['readFraction', 'readCount', 'sampleId', 'ntLengthOfCDR3', 'aaLengthOfCDR3',
                   'ntLengthOfVJJunction', 'AddedNucleotides', 'N2StrengthofCDR3', 'N2HydrophobicityofCDR3', 'N2VolumeofCDR3', 'N2SurfaceofCDR3', 'ChargeofCDR3']
    biophysics = table.groupby('sampleId', as_index=False).sum()
    biophysics = biophysics[['sampleId', 'ntLengthOfCDR3', 'aaLengthOfCDR3', 'ntLengthOfVJJunction', 'AddedNucleotides', 'N2StrengthofCDR3', 'N2HydrophobicityofCDR3', 'N2VolumeofCDR3', 'N2SurfaceofCDR3', 'ChargeofCDR3']]
    biophysics = biophysics.rename(columns={
            'AddedNucleotides': 'Added nucleotides',
            'ChargeofCDR3': 'Charge of CDR3',
            'N2HydrophobicityofCDR3': 'N2Hydrophobicity of CDR3',
            'N2StrengthofCDR3': 'N2Strength of CDR3',
            'N2SurfaceofCDR3': 'N2Surface of CDR3',
            'N2VolumeofCDR3': 'N2Volume of CDR3',
            'aaLengthOfCDR3': 'Length of CDR3, aa',
            'ntLengthOfCDR3': 'Length of CDR3, nt',
            'ntLengthOfVJJunction': 'Length of VJJunction, nt'
        })
    biophysics.set_index('sampleId', inplace=True)
    test_biophysics = test_table_format(biophysics)
    test_biophysics.to_csv(f'individual_{downsampling}_test/test.cdr3metrics.tsv', sep='\t', index=False)


def chao1(table):
    singletons = table[table['readCount']==1.]['readFraction'].count()
    doubletons = table[table['readCount']==2.]['readFraction'].count()
    f0 = singletons * (singletons - 1) / 2 / (doubletons + 1)
    observed = table['readCount'].count()
    chao1 = observed + f0
    return chao1


def d50_df(table):
    table['clonotype_number'] = np.arange(table.shape[0]) + 1
    if any(np.isclose(table['readFraction'].cumsum(), 0.5)):
        return pd.DataFrame(table[np.isclose(table['readFraction'].cumsum(), 0.5)], columns=table.columns)
    else:
        return pd.DataFrame(table[table['readFraction'].cumsum() >= 0.5].head(1), columns=table.columns)


def efronThisted(table):
    for depth in range(1, 21):
        h = np.zeros(depth)
        nx = np.zeros(depth)
        for y in range(1, depth+1):
            nx[y-1] = len(table[table['readCount'] == y].index)
            for x in range(1, y+1):
                coeff = binom(y-1, x-1)
                if x % 2 == 1:
                    h[x-1] += coeff
                else:
                    h[x-1] -= coeff
        l = []
        p = []
        for i in range(depth):
            l.append(h[i] * nx[i])
            p.append(h[i] * h[i] * nx[i])
        S = len(table.index) + sum(l)
        D = np.sqrt(sum(p))
        CV = D / S
        if CV >= 0.05:
            break
    return S


def diversity(table, downsampling):
    metrics_list = []
    samples = table['sampleId'].unique()
    for sample in samples:
        temp_table = table[table['sampleId']==sample]
        observed_diversity = len(temp_table.index)
        shannonWienerIndex = -(temp_table['readFraction'] * np.log(temp_table['readFraction'])).sum()
        shannonWiener = np.exp(shannonWienerIndex)
        normalizedShannonWiener = shannonWienerIndex / np.log(len(temp_table.index))
        inverseSimpson = 1 / (temp_table['readFraction'] * temp_table['readFraction']).sum()
        gini = 1 - (-1 / inverseSimpson)
        chao1_estimate = chao1(temp_table)
        df_d50 = d50_df(temp_table)
        d50 = df_d50['clonotype_number'].values[0]
        efronThisted_estimate = efronThisted(temp_table)
        metrics_list.append((sample, observed_diversity, shannonWiener, normalizedShannonWiener, inverseSimpson, gini, chao1_estimate, d50, efronThisted_estimate))

    diversity_table = pd.DataFrame(metrics_list, columns=['sampleId', 'Observed diversity', 'Shannon-Wiener diversity', 'Normalized Shannon-Wiener index', 'Inverse Simpson index', 'Gini index', 'Chao1 estimate', 'd50', 'Efron-Thisted estimate'])
    diversity_table.set_index('sampleId', inplace=True)
    diversity_final = test_table_format(diversity_table)
    diversity_final.to_csv(f'individual_{downsampling}_test/test.diversity.tsv', sep='\t', index=False)


def individual_pipeline(downsampling):
    create_folder(downsampling)
    tables = read_tables(downsampling)
    vj_usage(tables, downsampling)
    biophysics(tables, downsampling)
    diversity(tables, downsampling)


def overlap(files_list, intersect_type):
    files_list = [file for file in files_list if file.split('/')[-1] != 'metadata.txt']
    metric_list = []
    l = itertools.permutations(files_list, 2)
    intersect_dict = {'CDR3|nt|V|J': ['nSeqCDR3', 'V', 'J'], 'CDR3|aa|V|J': ['aaSeqCDR3', 'V', 'J'],
                      'CDR3|nt': ['nSeqCDR3'], 'CDR3|aa': ['aaSeqCDR3']}
    for pairs in l:
        sample_id_1 = basename(pairs[0]).split('.')[0] + '.clns'
        sample_id_2 = basename(pairs[1]).split('.')[0] + '.clns'
        table_1 = pd.read_table(pairs[0], sep='\t')
        table_2 = pd.read_table(pairs[1], sep='\t')
        table_1['V'] = table_1['allVHitsWithScore'].apply(lambda x: x.split('*')[0])
        table_1['J'] = table_1['allJHitsWithScore'].apply(lambda x: x.split('*')[0])
        table_2['V'] = table_2['allVHitsWithScore'].apply(lambda x: x.split('*')[0])
        table_2['J'] = table_2['allJHitsWithScore'].apply(lambda x: x.split('*')[0])
        table_1_grouped = table_1.groupby(intersect_dict[intersect_type], as_index=False)['readFraction'].sum()
        table_2_grouped = table_2.groupby(intersect_dict[intersect_type], as_index=False)['readFraction'].sum()
        table_merged = pd.merge(table_1_grouped, table_2_grouped, on=intersect_dict[intersect_type], how='inner', suffixes=['_1', '_2'])
        if len(table_merged.index) == 0:
            sharedClonotypes = 0
            corr = 0
            D = 0
            F1 = 0
            F2 = 0
            Jaccard = 0
        else:
            sharedClonotypes = len(table_merged.index)
            corr = linregress(table_merged.readFraction_1, table_merged.readFraction_2).rvalue
            D = len(table_merged.index) / len(table_1_grouped.index) / len(table_2_grouped.index)
            F1 = np.sqrt(table_merged.readFraction_1.sum() * table_merged.readFraction_2.sum())
            F2 = (np.sqrt(table_merged.readFraction_1 * table_merged.readFraction_2)).sum()
            Jaccard = len(table_merged.index) / (len(table_1_grouped.index) + len(table_2_grouped.index) - len(table_merged.index))
        metric_list.append((sample_id_1, sample_id_2, sharedClonotypes, corr, D, F1, F2, Jaccard))
    intersect_table = pd.DataFrame(metric_list, columns=['1_sample_id', '2_sample_id', 'SharedClonotypes', 'Pearson', 'RelativeDiversity', 'F1Index', 'F2Index', 'JaccardIndex'])
    intersect_table['Pearson'] = intersect_table['Pearson'].replace(-1, 0)
    intersect_table['Pearson'] = intersect_table['Pearson'].round(8)
    intersection_type_list = ['SharedClonotypes', 'Pearson', 'RelativeDiversity', 'F1Index', 'F2Index', 'JaccardIndex']
    folder_name = ''.join(intersect_type.split('|'))
    for metric in intersection_type_list:
        table = intersect_table[['1_sample_id', '2_sample_id', metric]]
        table.to_csv(f'overlap_{folder_name}_test/none.{metric}.tsv', sep='\t', index=False)


def overlap_pipeline(files_list, intersect_type):
    folder_name = ''.join(intersect_type.split('|'))
    if not isdir(f'overlap_{folder_name}_test'):
        mkdir(f'overlap_{folder_name}_test')
    overlap(files_list, intersect_type)



#############################################################################################################
######################################  TEST  ###############################################################
#############################################################################################################



INDIVIDUAL_METRICS = ['jUsage', 'vjUsage', 'vUsage', 'diversity', 'cdr3metrics']
OVERLAP_METRICS = ['RelativeDiversity', 'Pearson', 'F1Index', 'F2Index', 'JaccardIndex', 'SharedClonotypes']
ASSERTION_ERRORS = []


def mixcr_tables_format(table):
    table.rename(columns={ table.columns[0]: "sampleId" }, inplace = True)
    table.sort_values(list(table.columns[:3]), inplace=True)
    table = table[sorted(table.columns)]
    table.reset_index(drop=True, inplace=True)
    return table


def filterCloneTables():
    files = glob.glob('*.tsv')
    cloneTables = [file for file in files if file.split('.') != 'metadata']
    return cloneTables


def compare_tables(analysis='individual', metric=None, downsampling=None, criteria=None):
    if analysis == 'individual':
        if metric is None:
            metrics = INDIVIDUAL_METRICS
        else:
            metrics = [metric]
        for metric in metrics:
            file_mixcr = glob.glob(f'individual/test.{metric}.TRB.tsv')
            if file_mixcr:
                file_test = glob.glob(f'individual_{downsampling}_test/*.{metric}*')
                table_mixcr = pd.read_table(*file_mixcr, sep='\t')
                table_mixcr_formatted = mixcr_tables_format(table_mixcr)
                table_test = pd.read_table(*file_test, sep='\t')
                try:
                    pd.testing.assert_frame_equal(table_mixcr_formatted, table_test, check_dtype=False)
                    print(f'{metric} test passed')
                except AssertionError as e:
                    print(f'{metric} test failed')
                    ASSERTION_ERRORS.append(f'{metric} test failed')
                    print(e)
            else:
                print(f'File for {metric} not found')
                ASSERTION_ERRORS.append(f'File for {metric} not found')
    elif analysis == 'overlap':
        if metric is None:
            metrics = OVERLAP_METRICS
        else:
            metrics = [metric]
        for metric in metrics:
            file_mixcr = glob.glob(f'{analysis}/*.{metric}.*')
            if file_mixcr:
                criteria_format = ''.join(criteria.split('|'))
                file_test_path = f'overlap_{criteria_format}_test/none.{metric}.tsv'
                file_test = glob.glob(file_test_path)
                table_test = pd.read_table(file_test_path, sep='\t')
                print(*file_mixcr)
                table_mixcr = pd.read_table(*file_mixcr, sep='\t', index_col=0)
                table_mixcr.index.name = 'sample_id'
                table_mixcr.reset_index(inplace=True)
                table_mixcr_melted = pd.melt(table_mixcr, id_vars='sample_id', value_vars=table_mixcr.columns[1:])
                table_mixcr_melted.columns = ['1_sample_id', '2_sample_id', metric]
                table_mixcr_final = table_mixcr_melted[pd.DataFrame(np.sort(table_mixcr_melted[['1_sample_id','2_sample_id']].values,1)).duplicated()].sort_values(['1_sample_id', '2_sample_id']).reset_index(drop=True)
                mixcr_sample_pairs = table_mixcr_final[['1_sample_id', '2_sample_id']]
                table_test_final = pd.merge(mixcr_sample_pairs, table_test, on=['1_sample_id', '2_sample_id'], how='left')
                table_test_final.sort_values(['1_sample_id', '2_sample_id'], inplace=True)
                table_test_final.fillna(0, inplace=True)
                table_test_final.reset_index(drop=True, inplace=True)
                if metric == 'Pearson':
                    table_mixcr_final['Pearson'] = table_mixcr_final['Pearson'].replace(-1, 0)
                try:
                    pd.testing.assert_frame_equal(table_mixcr_final.fillna(0), table_test_final, check_dtype=False)
                    print(f'{metric} test passed')
                except AssertionError as e:
                    print(f'{metric} test failed')
                    ASSERTION_ERRORS.append(f'{metric} test failed')
                    print(e)
            else:
                print(f'File for {metric} not found')
                ASSERTION_ERRORS.append(f'File for {metric} not found')


def mixcr_align(fastq_file):
    mixcr_align_args = ['mixcr', 'align', '-p','legacy-4.0-default', '-f', '-s', 'hs',
                        fastq_file, fastq_file.split("_R2")[0] + '.vdjca']
    mixcr_alignment = subprocess.Popen(mixcr_align_args, stdout=None, stderr=None)
    mixcr_alignment.wait()


def mixcr_assemble(vdjca_file):
    mixcr_assemble_args = ['mixcr', 'assemble', '-f', vdjca_file + '.vdjca', vdjca_file + '.clns']
    mixcr_assemble = subprocess.Popen(mixcr_assemble_args, stdout=None, stderr=None)
    mixcr_assemble.wait()


def mixcr_exportClones(clns_file, txt_file = None):
    if txt_file == None:
        mixcr_exportClones_args = ['mixcr', 'exportClones', '-f', '-t', '-o', '-c', 'TRB', '-nFeature', 'VJJunction',
                                   clns_file + '.clns', clns_file + '.tsv']
    else:
        mixcr_exportClones_args = ['mixcr', 'exportClones', '-f', '-t', '-o', '-c', 'TRB', '-nFeature', 'VJJunction',
                                   clns_file + '.clns', txt_file + '.tsv']
    mixcr_exportClones = subprocess.Popen(mixcr_exportClones_args)
    mixcr_exportClones.wait()

def downsample(downsampling):
    clns_files = glob.glob('*.clns')
    downsample_args = ['mixcr', 'downsample', '--only-productive', '--chains', 'TRB', '--out', 'downsampled_' +  downsampling,
                       '--downsampling', downsampling, *clns_files]
    downsample = subprocess.Popen(downsample_args)
    downsample.wait()


def postanalys_individual(downsampling, overrideDiversityDownsampling=None):
    clns_files = glob.glob('*.clns')
    pa_individual_args = ['mixcr', 'postanalysis', 'individual','-f', '--only-productive',
                         '--default-downsampling', downsampling,
                         '--default-weight-function', 'read',
                         '--chains', 'TRB', '--metadata', 'metadata.tsv', '--tables', 'individual/test.tsv', '--preproc-tables',
                         'individual/.tsv', *clns_files, 'individual/results.json']
    if overrideDiversityDownsampling:
        pa_individual_args = pa_individual_args + [f'-Odiversity.downsampling={overrideDiversityDownsampling}']
    pa_individual = subprocess.Popen(pa_individual_args)
    pa_individual.wait()


def postanalys_overlap(downsampling, intersect_type):
    clns_files = glob.glob('*.clns')
    pa_overlap_args = ['mixcr', 'postanalysis', 'overlap','-f', '--only-productive',
                      '--default-downsampling', downsampling,
                      '--default-weight-function', 'read',
                      '--chains', 'TRB', '--metadata', 'metadata.tsv', '--criteria', intersect_type, '--tables',
                      'overlap/test.tsv', '--preproc-tables', 'overlap/.tsv', *clns_files, 'overlap/result.json']
    pa_overlap = subprocess.Popen(pa_overlap_args)
    pa_overlap.wait()


def pipeline():
    for files in glob.glob('*.fastq.gz'):
        sample_name = files.split('_R2')[0]
        mixcr_align(files)
        mixcr_assemble(sample_name)
        mixcr_exportClones(sample_name)


#test individual with --only-productive, --default-downsampling {count-reads-auto, top-reads-1000, cumtop-reads-50}
def test_case1():
    print('\n')
    print('====Testing individual====')
    downsampling_methods = ['count-reads-auto', 'top-reads-1000', 'cumtop-reads-50']
    for ds in downsampling_methods:
        print('\n')
        print(f'Downsampling method - {ds}')
        downsample(ds)
        try:
    	    if isdir(f'downsampled_{ds}'):
    	        for files in glob.glob(f'downsampled_{ds}/*.TRB.downsampled.clns'):
                    name = files.split('.')[0]
                    mixcr_exportClones(files.split('.clns')[0], txt_file = name)
        except FileNotFoundError as e:
            print('Downsample doesnt work properly')
            ASSERTION_ERRORS.append('Downsample doesnt work properly')
        individual_pipeline(ds)
        postanalys_individual(ds)
        compare_tables(analysis='individual', downsampling=ds)
        shutil.rmtree('individual/')


# test overlap with --only-productive, --default-downsampling none,
# --criteria ["CDR3|nt|V|J", "CDR3|aa|V|J", "CDR3|aa", "CDR3|nt"]
def test_case2():
    print('\n')
    print('====Testing overlap without downsampling====')
    criteria_list = ["CDR3|nt|V|J", "CDR3|nt", "CDR3|aa", "CDR3|aa|V|J"]
    for criteria in criteria_list:
        print('\n')
        print(f'Intersect type - {criteria}')
        overlap_pipeline(glob.glob('*_beta.tsv'), criteria)
        print('done')
        postanalys_overlap('none', criteria)
        compare_tables(analysis='overlap', criteria=criteria)
        shutil.rmtree('overlap/')


if __name__ == '__main__':
    pipeline()
    test_case1()
    test_case2()
    if len(ASSERTION_ERRORS) > 0:
        for err in ASSERTION_ERRORS:
            print(f'ERROR: ${err}')
        raise SystemExit('Some tests were not passed')
    else:
        print('All tests passed')
